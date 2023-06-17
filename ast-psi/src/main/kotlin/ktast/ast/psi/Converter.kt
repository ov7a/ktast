package ktast.ast.psi

import ktast.ast.Node
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import kotlin.reflect.full.createInstance

/**
 * Converts PSI elements to AST nodes. It's not meant to be used directly, use [Parser] instead.
 */
open class Converter {
    /**
     * Method to be called when a node is created.
     *
     * @param node The node that was created
     * @param element The PSI element that was used to create the node.
     */
    protected open fun onNode(node: Node, element: PsiElement) {}

    open fun convert(v: KtFile): Node.KotlinFile = convertKotlinFile(v)

    protected fun convertKotlinFile(v: KtFile) = Node.KotlinFile(
        annotationSets = convertAnnotationSets(v.fileAnnotationList),
        packageDirective = v.packageDirective?.takeIf { it.packageNames.isNotEmpty() }?.let(::convertPackageDirective),
        importDirectives = v.importList?.imports?.map(::convertImportDirective) ?: listOf(),
        declarations = v.declarations.map(::convertDeclaration)
    ).map(v)

    protected fun convertPackageDirective(v: KtPackageDirective): Node.PackageDirective {
        if (v.modifierList != null) {
            throw Unsupported("Package directive with modifiers is not supported")
        }
        return Node.PackageDirective(
            names = v.packageNames.map(::convertNameExpression),
        ).map(v)
    }

    protected fun convertImportDirective(v: KtImportDirective) = Node.ImportDirective(
        names = convertImportNames(v.importedReference ?: error("No imported reference for $v"))
                + listOfNotNull(v.asterisk?.let(::convertNameExpression)),
        aliasName = v.alias?.nameIdentifier?.let(::convertNameExpression),
    ).map(v)

    protected fun convertImportNames(v: KtExpression): List<Node.Expression.NameExpression> = when (v) {
        // Flatten nest of KtDotQualifiedExpression into list.
        is KtDotQualifiedExpression ->
            convertImportNames(v.receiverExpression) + listOf(
                convertNameExpression(
                    v.selectorExpression as? KtNameReferenceExpression ?: error("No name reference for $v")
                )
            )
        is KtReferenceExpression -> listOf(convertNameExpression(v))
        else -> error("Unexpected type $v")
    }

    protected fun convertStatement(v: KtExpression): Node.Statement = when (v) {
        is KtForExpression -> convertForStatement(v)
        is KtWhileExpression -> convertWhileStatement(v)
        is KtDoWhileExpression -> convertDoWhileStatement(v)
        is KtDeclaration -> convertDeclaration(v)
        else -> convertExpression(v) as? Node.Statement ?: error("Unrecognized statement $v")
    }

    protected fun convertForStatement(v: KtForExpression) = Node.Statement.ForStatement(
        lPar = convertKeyword(v.leftParenthesis ?: error("No left parenthesis for $v")),
        loopParam = convertLambdaParam(v.loopParameter ?: error("No param on for $v")),
        inKeyword = convertKeyword(v.inKeyword ?: error("No in keyword for $v")),
        loopRange = convertExpression(v.loopRange ?: error("No loop range expression for $v")),
        rPar = convertKeyword(v.rightParenthesis ?: error("No right parenthesis for $v")),
        body = convertExpression(v.body ?: error("No body expression for $v")),
    ).map(v)

    protected fun convertWhileStatement(v: KtWhileExpression) = Node.Statement.WhileStatement(
        lPar = convertKeyword(v.leftParenthesis ?: error("No left parenthesis for $v")),
        condition = convertExpression(v.condition ?: error("No condition expression for $v")),
        rPar = convertKeyword(v.rightParenthesis ?: error("No right parenthesis for $v")),
        body = convertExpression(v.body ?: error("No body expression for $v")),
    ).map(v)

    protected fun convertDoWhileStatement(v: KtDoWhileExpression) = Node.Statement.DoWhileStatement(
        body = convertExpression(v.body ?: error("No body expression for $v")),
        lPar = convertKeyword(v.leftParenthesis ?: error("No left parenthesis for $v")),
        condition = convertExpression(v.condition ?: error("No condition expression for $v")),
        rPar = convertKeyword(v.rightParenthesis ?: error("No right parenthesis for $v")),
    ).map(v)

    protected fun convertDeclaration(v: KtDeclaration): Node.Declaration = when (v) {
        is KtEnumEntry -> error("KtEnumEntry is handled in convertEnumEntry")
        is KtClassOrObject -> convertClassDeclaration(v)
        is KtAnonymousInitializer -> convertInitializer(v)
        is KtNamedFunction -> convertFunctionDeclaration(v)
        is KtDestructuringDeclaration -> convertPropertyDeclaration(v)
        is KtProperty -> convertPropertyDeclaration(v)
        is KtTypeAlias -> convertTypeAliasDeclaration(v)
        is KtSecondaryConstructor -> convertSecondaryConstructor(v)
        else -> error("Unrecognized declaration type for $v")
    }

    protected fun convertClassDeclaration(v: KtClassOrObject) = Node.Declaration.ClassDeclaration(
        modifiers = convertModifiers(v.modifierList),
        classDeclarationKeyword = v.getDeclarationKeyword()?.let(::convertKeyword)
            ?: error("declarationKeyword not found"),
        name = v.nameIdentifier?.let(::convertNameExpression),
        lAngle = v.typeParameterList?.leftAngle?.let(::convertKeyword),
        typeParams = convertTypeParams(v.typeParameterList),
        rAngle = v.typeParameterList?.rightAngle?.let(::convertKeyword),
        primaryConstructor = v.primaryConstructor?.let(::convertPrimaryConstructor),
        classParents = convertClassParents(v.getSuperTypeList()),
        typeConstraintSet = v.typeConstraintList?.let { typeConstraintList ->
            convertTypeConstraintSet(v, typeConstraintList)
        },
        classBody = v.body?.let(::convertClassBody),
    ).map(v)

    protected fun convertClassParents(v: KtSuperTypeList?): List<Node.Declaration.ClassDeclaration.ClassParent> =
        v?.entries.orEmpty().map(::convertClassParent)

    protected fun convertClassParent(v: KtSuperTypeListEntry) = when (v) {
        is KtSuperTypeCallEntry -> convertConstructorClassParent(v)
        is KtDelegatedSuperTypeEntry -> convertDelegationClassParent(v)
        is KtSuperTypeEntry -> convertTypeClassParent(v)
        else -> error("Unknown super type entry $v")
    }

    protected fun convertConstructorClassParent(v: KtSuperTypeCallEntry) =
        Node.Declaration.ClassDeclaration.ConstructorClassParent(
            type = v.typeReference?.let(::convertType) as? Node.Type.SimpleType
                ?: error("Bad type on super call $v"),
            lPar = v.valueArgumentList?.leftParenthesis?.let(::convertKeyword)
                ?: error("No left parenthesis for $v"),
            args = convertValueArgs(v.valueArgumentList) ?: error("No value arguments for $v"),
            rPar = v.valueArgumentList?.rightParenthesis?.let(::convertKeyword)
                ?: error("No right parenthesis for $v"),
        ).map(v)

    protected fun convertDelegationClassParent(v: KtDelegatedSuperTypeEntry) =
        Node.Declaration.ClassDeclaration.DelegationClassParent(
            type = v.typeReference?.let(::convertType)
                ?: error("No type on delegated super type $v"),
            expression = convertExpression(v.delegateExpression ?: error("Missing delegateExpression for $v")),
        ).map(v)

    protected fun convertTypeClassParent(v: KtSuperTypeEntry) = Node.Declaration.ClassDeclaration.TypeClassParent(
        type = v.typeReference?.let(::convertType)
            ?: error("No type on super type $v"),
    ).map(v)

    protected fun convertPrimaryConstructor(v: KtPrimaryConstructor) =
        Node.Declaration.ClassDeclaration.PrimaryConstructor(
            modifiers = convertModifiers(v.modifierList),
            constructorKeyword = v.getConstructorKeyword()?.let(::convertKeyword),
            lPar = v.valueParameterList?.leftParenthesis?.let(::convertKeyword),
            params = convertFuncParams(v.valueParameterList),
            rPar = v.valueParameterList?.rightParenthesis?.let(::convertKeyword),
        ).map(v)

    protected fun convertClassBody(v: KtClassBody): Node.Declaration.ClassDeclaration.ClassBody {
        val ktEnumEntries = v.declarations.filterIsInstance<KtEnumEntry>()
        val declarationsExcludingKtEnumEntry = v.declarations.filter { it !is KtEnumEntry }
        return Node.Declaration.ClassDeclaration.ClassBody(
            enumEntries = ktEnumEntries.map(::convertEnumEntry),
            declarations = declarationsExcludingKtEnumEntry.map(::convertDeclaration),
        ).map(v)
    }

    protected fun convertEnumEntry(v: KtEnumEntry): Node.Declaration.ClassDeclaration.ClassBody.EnumEntry =
        Node.Declaration.ClassDeclaration.ClassBody.EnumEntry(
            modifiers = convertModifiers(v.modifierList),
            name = v.nameIdentifier?.let(::convertNameExpression) ?: error("Unnamed enum"),
            lPar = v.initializerList?.valueArgumentList?.leftParenthesis?.let(::convertKeyword),
            args = convertValueArgs(v.initializerList?.valueArgumentList),
            rPar = v.initializerList?.valueArgumentList?.rightParenthesis?.let(::convertKeyword),
            classBody = v.body?.let(::convertClassBody),
        ).map(v)

    protected fun convertInitializer(v: KtAnonymousInitializer): Node.Declaration.ClassDeclaration.ClassBody.Initializer {
        if (v.modifierList != null) {
            throw Unsupported("Anonymous initializer with modifiers not supported")
        }
        return Node.Declaration.ClassDeclaration.ClassBody.Initializer(
            block = convertBlockExpression(v.body as? KtBlockExpression ?: error("No init block for $v")),
        ).map(v)
    }

    protected fun convertSecondaryConstructor(v: KtSecondaryConstructor) =
        Node.Declaration.ClassDeclaration.ClassBody.SecondaryConstructor(
            modifiers = convertModifiers(v.modifierList),
            constructorKeyword = convertKeyword(v.getConstructorKeyword()),
            lPar = v.valueParameterList?.leftParenthesis?.let(::convertKeyword),
            params = convertFuncParams(v.valueParameterList),
            rPar = v.valueParameterList?.rightParenthesis?.let(::convertKeyword),
            delegationCall = if (v.hasImplicitDelegationCall()) null else convertCallExpression(
                v.getDelegationCall()
            ),
            block = v.bodyExpression?.let(::convertBlockExpression)
        ).map(v)

    protected fun convertFunctionDeclaration(v: KtNamedFunction): Node.Declaration.FunctionDeclaration {
        if (v.typeParameterList != null) {
            val hasTypeParameterListBeforeFunctionName = v.allChildren.find {
                it is KtTypeParameterList || it is KtTypeReference || it.node.elementType == KtTokens.IDENTIFIER
            } is KtTypeParameterList
            if (!hasTypeParameterListBeforeFunctionName) {
                // According to the Kotlin syntax, type parameters are not allowed here. However, Kotlin compiler can parse them.
                throw Unsupported("Type parameters after function name is not allowed")
            }
        }

        return Node.Declaration.FunctionDeclaration(
            modifiers = convertModifiers(v.modifierList),
            lAngle = v.typeParameterList?.leftAngle?.let(::convertKeyword),
            typeParams = convertTypeParams(v.typeParameterList),
            rAngle = v.typeParameterList?.rightAngle?.let(::convertKeyword),
            receiverType = v.receiverTypeReference?.let(::convertType),
            name = v.nameIdentifier?.let(::convertNameExpression),
            lPar = v.valueParameterList?.leftParenthesis?.let(::convertKeyword),
            params = convertFuncParams(v.valueParameterList),
            rPar = v.valueParameterList?.rightParenthesis?.let(::convertKeyword),
            returnType = v.typeReference?.let(::convertType),
            postModifiers = convertPostModifiers(v),
            equals = v.equalsToken?.let(::convertKeyword),
            body = v.bodyExpression?.let { convertExpression(it) },
        ).map(v)
    }

    protected fun convertPropertyDeclaration(v: KtProperty) = Node.Declaration.PropertyDeclaration(
        modifiers = convertModifiers(v.modifierList),
        valOrVarKeyword = convertKeyword(v.valOrVarKeyword),
        lAngle = v.typeParameterList?.leftAngle?.let(::convertKeyword),
        typeParams = convertTypeParams(v.typeParameterList),
        rAngle = v.typeParameterList?.rightAngle?.let(::convertKeyword),
        receiverType = v.receiverTypeReference?.let(::convertType),
        lPar = null,
        variables = listOf(convertVariable(v)),
        rPar = null,
        typeConstraintSet = v.typeConstraintList?.let { typeConstraintList ->
            convertTypeConstraintSet(v, typeConstraintList)
        },
        equals = v.equalsToken?.let(::convertKeyword),
        initializer = v.initializer?.let(::convertExpression),
        propertyDelegate = v.delegate?.let(::convertPropertyDelegate),
        accessors = v.accessors.map(::convertPropertyAccessor),
    ).map(v)

    protected fun convertPropertyDeclaration(v: KtDestructuringDeclaration) = Node.Declaration.PropertyDeclaration(
        modifiers = convertModifiers(v.modifierList),
        valOrVarKeyword = v.valOrVarKeyword?.let(::convertKeyword) ?: error("Missing valOrVarKeyword"),
        lAngle = null,
        typeParams = listOf(),
        rAngle = null,
        receiverType = null,
        lPar = v.lPar?.let(::convertKeyword),
        variables = v.entries.map(::convertVariable),
        rPar = v.rPar?.let(::convertKeyword),
        typeConstraintSet = null,
        equals = convertKeyword(v.equalsToken),
        initializer = v.initializer?.let(::convertExpression),
        propertyDelegate = null,
        accessors = listOf(),
    ).map(v)

    protected fun convertPropertyDelegate(v: KtPropertyDelegate) =
        Node.Declaration.PropertyDeclaration.PropertyDelegate(
            expression = convertExpression(v.expression ?: error("Missing expression for $v")),
        ).map(v)

    protected fun convertPropertyAccessor(v: KtPropertyAccessor): Node.Declaration.PropertyDeclaration.Accessor =
        when (v.isGetter) {
            true -> convertGetter(v)
            false -> convertSetter(v)
        }

    protected fun convertGetter(v: KtPropertyAccessor) = Node.Declaration.PropertyDeclaration.Getter(
        modifiers = convertModifiers(v.modifierList),
        lPar = v.leftParenthesis?.let(::convertKeyword),
        rPar = v.rightParenthesis?.let(::convertKeyword),
        type = v.returnTypeReference?.let(::convertType),
        postModifiers = convertPostModifiers(v),
        equals = v.equalsToken?.let(::convertKeyword),
        body = v.bodyExpression?.let(::convertExpression),
    ).map(v)

    protected fun convertSetter(v: KtPropertyAccessor) = Node.Declaration.PropertyDeclaration.Setter(
        modifiers = convertModifiers(v.modifierList),
        lPar = v.leftParenthesis?.let(::convertKeyword),
        params = convertLambdaParams(v.parameterList),
        rPar = v.rightParenthesis?.let(::convertKeyword),
        postModifiers = convertPostModifiers(v),
        equals = v.equalsToken?.let(::convertKeyword),
        body = v.bodyExpression?.let(::convertExpression),
    ).map(v)

    protected fun convertTypeAliasDeclaration(v: KtTypeAlias) = Node.Declaration.TypeAliasDeclaration(
        modifiers = convertModifiers(v.modifierList),
        name = v.nameIdentifier?.let(::convertNameExpression) ?: error("No type alias name for $v"),
        lAngle = v.typeParameterList?.leftAngle?.let(::convertKeyword),
        typeParams = convertTypeParams(v.typeParameterList),
        rAngle = v.typeParameterList?.rightAngle?.let(::convertKeyword),
        equals = convertKeyword(v.equalsToken),
        type = convertType(v.getTypeReference() ?: error("No type alias ref for $v"))
    ).map(v)

    protected fun convertType(v: KtTypeReference): Node.Type {
        return convertType(v, v.nonExtraChildren())
    }

    protected fun convertType(v: KtElement, targetChildren: List<PsiElement>): Node.Type {
        require(v is KtTypeReference || v is KtNullableType) { "Unexpected type: $v" }

        val modifierListElements = targetChildren.takeWhile { it is KtModifierList }
        check(modifierListElements.size <= 1) { "Multiple modifier lists in type children: $targetChildren" }
        val modifierList = modifierListElements.firstOrNull() as? KtModifierList
        val questionMarks = targetChildren.takeLastWhile { it.node.elementType == KtTokens.QUEST }
        val restChildren =
            targetChildren.subList(modifierListElements.size, targetChildren.size - questionMarks.size)

        // questionMarks can be ignored here because when v is KtNullableType, it will be handled in caller side.
        if (restChildren.first().node.elementType == KtTokens.LPAR && restChildren.last().node.elementType == KtTokens.RPAR) {
            return Node.Type.ParenthesizedType(
                modifiers = convertModifiers(modifierList),
                lPar = convertKeyword(restChildren.first()),
                innerType = convertType(v, restChildren.subList(1, restChildren.size - 1)),
                rPar = convertKeyword(restChildren.last()),
            ).map(v)
        }

        return when (val typeEl = restChildren.first()) {
            is KtFunctionType -> convertFunctionType(v, modifierList, typeEl)
            is KtUserType -> convertSimpleType(v, modifierList, typeEl)
            is KtNullableType -> convertNullableType(v, modifierList, typeEl)
            is KtDynamicType -> convertDynamicType(v, modifierList, typeEl)
            else -> error("Unrecognized type of $typeEl")
        }
    }

    protected fun convertNullableType(v: KtElement, modifierList: KtModifierList?, typeEl: KtNullableType) =
        Node.Type.NullableType(
            modifiers = convertModifiers(modifierList),
            innerType = convertType(typeEl, typeEl.nonExtraChildren()),
            questionMark = convertKeyword(typeEl.questionMark),
        ).map(v)

    protected fun convertSimpleType(v: KtElement, modifierList: KtModifierList?, typeEl: KtUserType) =
        Node.Type.SimpleType(
            modifiers = convertModifiers(modifierList),
            pieces = generateSequence(typeEl) { it.qualifier }.toList().reversed()
                .map { convertSimpleTypePiece(v, it) },
        ).map(v)

    protected fun convertSimpleTypePiece(v: KtElement, typeEl: KtUserType) = Node.Type.SimpleType.SimpleTypePiece(
        name = convertNameExpression(typeEl.referenceExpression ?: error("No type name for $typeEl")),
        lAngle = typeEl.typeArgumentList?.leftAngle?.let(::convertKeyword),
        typeArgs = convertTypeArgs(typeEl.typeArgumentList),
        rAngle = typeEl.typeArgumentList?.rightAngle?.let(::convertKeyword),
    ).map(v)

    protected fun convertSimpleTypePiece(v: PsiElement) = Node.Type.SimpleType.SimpleTypePiece(
        name = convertNameExpression(v),
        lAngle = null,
        typeArgs = listOf(),
        rAngle = null,
    ).map(v)

    protected fun convertDynamicType(v: KtElement, modifierList: KtModifierList?, typeEl: KtDynamicType) =
        Node.Type.DynamicType(
            modifiers = convertModifiers(modifierList),
        ).map(v)

    protected fun convertFunctionType(v: KtElement, modifierList: KtModifierList?, typeEl: KtFunctionType) =
        Node.Type.FunctionType(
            modifiers = convertModifiers(modifierList),
            contextReceiver = typeEl.contextReceiverList?.let(::convertContextReceiver),
            receiverType = typeEl.receiver?.typeReference?.let(::convertType),
            lPar = typeEl.parameterList?.leftParenthesis?.let(::convertKeyword),
            params = convertTypeFunctionParams(typeEl.parameterList),
            rPar = typeEl.parameterList?.rightParenthesis?.let(::convertKeyword),
            returnType = convertType(typeEl.returnTypeReference ?: error("No return type for $typeEl")),
        ).map(v)

    protected fun convertTypeFunctionParams(v: KtParameterList?): List<Node.Type.FunctionType.FunctionTypeParam> =
        v?.parameters.orEmpty().map(::convertTypeFunctionParam)

    protected fun convertTypeFunctionParam(v: KtParameter) = Node.Type.FunctionType.FunctionTypeParam(
        name = v.nameIdentifier?.let(::convertNameExpression),
        type = convertType(v.typeReference ?: error("No param type"))
    ).map(v)

    protected fun convertExpression(v: KtExpression): Node.Expression = when (v) {
        is KtIfExpression -> convertIfExpression(v)
        is KtTryExpression -> convertTryExpression(v)
        is KtWhenExpression -> convertWhenExpression(v)
        is KtThrowExpression -> convertThrowExpression(v)
        is KtReturnExpression -> convertReturnExpression(v)
        is KtContinueExpression -> convertContinueExpression(v)
        is KtBreakExpression -> convertBreakExpression(v)
        is KtBlockExpression -> convertBlockExpression(v)
        is KtCallExpression -> convertCallExpression(v)
        is KtLambdaExpression -> convertLambdaExpression(v)
        is KtFunctionLiteral -> error("Supposed to be unreachable here. KtFunctionLiteral is expected to be inside of KtLambdaExpression.")
        is KtBinaryExpression -> convertBinaryExpression(v)
        is KtQualifiedExpression -> convertBinaryExpression(v)
        is KtPrefixExpression -> convertPrefixUnaryExpression(v)
        is KtPostfixExpression -> convertPostfixUnaryExpression(v)
        is KtBinaryExpressionWithTypeRHS -> convertBinaryTypeExpression(v)
        is KtIsExpression -> convertBinaryTypeExpression(v)
        is KtCallableReferenceExpression -> convertCallableReferenceExpression(v)
        is KtClassLiteralExpression -> convertClassLiteralExpression(v)
        is KtParenthesizedExpression -> convertParenthesizedExpression(v)
        is KtStringTemplateExpression -> convertStringLiteralExpression(v)
        is KtConstantExpression -> convertConstantLiteralExpression(v)
        is KtObjectLiteralExpression -> convertObjectLiteralExpression(v)
        is KtCollectionLiteralExpression -> convertCollectionLiteralExpression(v)
        is KtThisExpression -> convertThisExpression(v)
        is KtSuperExpression -> convertSuperExpression(v)
        is KtConstructorDelegationReferenceExpression -> convertThisOrSuperExpression(v)
        is KtSimpleNameExpression -> convertNameExpression(v)
        is KtLabeledExpression -> convertLabeledExpression(v)
        is KtAnnotatedExpression -> convertAnnotatedExpression(v)
        is KtConstructorCalleeExpression -> error("Supposed to be unreachable here. KtConstructorCalleeExpression is expected to be inside of KtSuperTypeCallEntry or KtAnnotationEntry.")
        is KtArrayAccessExpression -> convertIndexedAccessExpression(v)
        is KtNamedFunction -> convertAnonymousFunctionExpression(v)
        // TODO: this is present in a recovery test where an interface decl is on rhs of a gt expr
        is KtClass -> throw Unsupported("Class expressions not supported")
        else -> error("Unrecognized expression type from $v")
    }

    protected fun convertIfExpression(v: KtIfExpression) = Node.Expression.IfExpression(
        lPar = convertKeyword(v.leftParenthesis ?: error("No left parenthesis on if for $v")),
        condition = convertExpression(v.condition ?: error("No cond on if for $v")),
        rPar = convertKeyword(v.rightParenthesis ?: error("No right parenthesis on if for $v")),
        body = convertExpression(v.then ?: error("No then body on if for $v")),
        elseBody = v.`else`?.let(::convertExpression),
    ).map(v)

    protected fun convertTryExpression(v: KtTryExpression) = Node.Expression.TryExpression(
        block = convertBlockExpression(v.tryBlock),
        catchClauses = v.catchClauses.map(::convertCatchClause),
        finallyBlock = v.finallyBlock?.finalExpression?.let(::convertBlockExpression)
    ).map(v)

    protected fun convertCatchClause(v: KtCatchClause) = Node.Expression.TryExpression.CatchClause(
        lPar = convertKeyword(v.parameterList?.leftParenthesis ?: error("No catch lpar for $v")),
        params = convertFuncParams(v.parameterList ?: error("No catch params for $v")),
        rPar = convertKeyword(v.parameterList?.rightParenthesis ?: error("No catch rpar for $v")),
        block = convertBlockExpression(v.catchBody as? KtBlockExpression ?: error("No catch block for $v")),
    ).map(v)

    protected fun convertWhenExpression(v: KtWhenExpression) = Node.Expression.WhenExpression(
        whenKeyword = convertKeyword(v.whenKeyword),
        subject = if (v.subjectExpression == null) null else convertWhenSubject(v),
        whenBranches = v.entries.map(::convertWhenBranch),
    ).map(v)

    protected fun convertWhenSubject(v: KtWhenExpression) = Node.Expression.WhenExpression.WhenSubject(
        lPar = convertKeyword(v.leftParenthesis ?: error("No left parenthesis for $v")),
        annotationSets = when (val expression = v.subjectExpression) {
            is KtProperty -> convertAnnotationSets(expression.modifierList)
            else -> listOf()
        },
        valKeyword = v.subjectVariable?.valOrVarKeyword?.let(::convertKeyword),
        variable = v.subjectVariable?.let(::convertVariable),
        expression = convertExpression(
            when (val expression = v.subjectExpression) {
                is KtProperty -> expression.initializer
                    ?: throw Unsupported("No initializer for when subject is not supported")
                is KtDestructuringDeclaration -> throw Unsupported("Destructuring declarations in when subject is not supported")
                null -> error("Supposed to be unreachable here. convertWhenSubject should be called only when subjectExpression is not null.")
                else -> expression
            }
        ),
        rPar = convertKeyword(v.rightParenthesis ?: error("No right parenthesis for $v")),
    ).map(v)

    protected fun convertWhenBranch(v: KtWhenEntry): Node.Expression.WhenExpression.WhenBranch = when (v.elseKeyword) {
        null -> convertConditionalWhenBranch(v)
        else -> convertElseWhenBranch(v)
    }

    protected fun convertConditionalWhenBranch(v: KtWhenEntry) = Node.Expression.WhenExpression.ConditionalWhenBranch(
        whenConditions = v.conditions.map(::convertWhenCondition),
        arrow = convertKeyword(v.arrow ?: error("No arrow symbol for $v")),
        body = convertExpression(v.expression ?: error("No when entry body for $v")),
    ).map(v)

    protected fun convertElseWhenBranch(v: KtWhenEntry) = Node.Expression.WhenExpression.ElseWhenBranch(
        arrow = convertKeyword(v.arrow ?: error("No arrow symbol for $v")),
        body = convertExpression(v.expression ?: error("No when entry body for $v")),
    ).map(v)

    protected fun convertWhenCondition(v: KtWhenCondition): Node.Expression.WhenExpression.WhenCondition = when (v) {
        is KtWhenConditionWithExpression -> convertExpressionWhenCondition(v)
        is KtWhenConditionInRange -> convertRangeWhenCondition(v)
        is KtWhenConditionIsPattern -> convertTypeWhenCondition(v)
        else -> error("Unrecognized when cond of $v")
    }

    protected fun convertExpressionWhenCondition(v: KtWhenConditionWithExpression) =
        Node.Expression.WhenExpression.ExpressionWhenCondition(
            expression = convertExpression(v.expression ?: error("No when cond expr for $v")),
        ).map(v)

    protected fun convertRangeWhenCondition(v: KtWhenConditionInRange) =
        Node.Expression.WhenExpression.RangeWhenCondition(
            operator = convertKeyword(v.operationReference),
            expression = convertExpression(v.rangeExpression ?: error("No when in expr for $v")),
        ).map(v)

    protected fun convertTypeWhenCondition(v: KtWhenConditionIsPattern) =
        Node.Expression.WhenExpression.TypeWhenCondition(
            operator = convertKeyword(v.firstChild),
            type = convertType(v.typeReference ?: error("No when is type for $v")),
        ).map(v)

    protected fun convertThrowExpression(v: KtThrowExpression) = Node.Expression.ThrowExpression(
        expression = convertExpression(v.thrownExpression ?: error("No throw expr for $v"))
    ).map(v)

    protected fun convertReturnExpression(v: KtReturnExpression) = Node.Expression.ReturnExpression(
        label = v.getTargetLabel()?.let(::convertNameExpression),
        expression = v.returnedExpression?.let(::convertExpression)
    ).map(v)

    protected fun convertContinueExpression(v: KtContinueExpression) = Node.Expression.ContinueExpression(
        label = v.getTargetLabel()?.let(::convertNameExpression),
    ).map(v)

    protected fun convertBreakExpression(v: KtBreakExpression) = Node.Expression.BreakExpression(
        label = v.getTargetLabel()?.let(::convertNameExpression),
    ).map(v)

    protected fun convertBlockExpression(v: KtBlockExpression) = Node.Expression.BlockExpression(
        statements = v.statements.map(::convertStatement),
    ).map(v)

    protected fun convertCallExpression(v: KtCallElement) = Node.Expression.CallExpression(
        calleeExpression = convertExpression(v.calleeExpression ?: error("No call expr for $v")),
        lAngle = v.typeArgumentList?.leftAngle?.let(::convertKeyword),
        typeArgs = convertTypeArgs(v.typeArgumentList),
        rAngle = v.typeArgumentList?.rightAngle?.let(::convertKeyword),
        lPar = v.valueArgumentList?.leftParenthesis?.let(::convertKeyword),
        args = convertValueArgs(v.valueArgumentList),
        rPar = v.valueArgumentList?.rightParenthesis?.let(::convertKeyword),
        lambdaArg = v.lambdaArguments.also {
            if (it.size >= 2) {
                // According to the Kotlin syntax, at most one lambda argument is allowed.
                // However, Kotlin compiler can parse multiple lambda arguments.
                throw Unsupported("At most one lambda argument is allowed")
            }
        }.firstOrNull()?.let(::convertLambdaArg)
    ).map(v)

    protected fun convertLambdaArg(v: KtLambdaArgument): Node.Expression.CallExpression.LambdaArg {
        var label: Node.Expression.NameExpression? = null
        var annotationSets: List<Node.Modifier.AnnotationSet> = emptyList()
        fun KtExpression.extractLambda(): KtLambdaExpression? = when (this) {
            is KtLambdaExpression -> this
            is KtLabeledExpression -> baseExpression?.extractLambda().also {
                label = convertNameExpression(getTargetLabel() ?: error("No label for $this"))
            }
            is KtAnnotatedExpression -> baseExpression?.extractLambda().also {
                annotationSets = convertAnnotationSets(this)
            }
            else -> null
        }

        val expr = v.getArgumentExpression()?.extractLambda() ?: error("No lambda for $v")
        return Node.Expression.CallExpression.LambdaArg(
            annotationSets = annotationSets,
            label = label,
            expression = convertLambdaExpression(expr)
        ).map(v)
    }

    protected fun convertLambdaExpression(v: KtLambdaExpression) = Node.Expression.LambdaExpression(
        params = convertLambdaParams(v.functionLiteral.valueParameterList),
        arrow = v.functionLiteral.arrow?.let(::convertKeyword),
        statements = v.bodyExpression?.statements.orEmpty().map(::convertStatement),
    ).map(v)

    protected fun convertBinaryExpression(v: KtBinaryExpression) = Node.Expression.BinaryExpression(
        lhs = convertExpression(v.left ?: error("No binary lhs for $v")),
        operator = if (v.operationReference.isConventionOperator()) {
            convertKeyword(v.operationReference)
        } else {
            convertNameExpression(v.operationReference.firstChild)
        },
        rhs = convertExpression(v.right ?: error("No binary rhs for $v"))
    ).map(v)

    protected fun convertBinaryExpression(v: KtQualifiedExpression) = Node.Expression.BinaryExpression(
        lhs = convertExpression(v.receiverExpression),
        operator = convertKeyword(v.operator),
        rhs = convertExpression(v.selectorExpression ?: error("No qualified rhs for $v"))
    ).map(v)

    protected fun convertPrefixUnaryExpression(v: KtPrefixExpression) = Node.Expression.PrefixUnaryExpression(
        operator = convertKeyword(v.operationReference),
        expression = convertExpression(v.baseExpression ?: error("No base expression for $v")),
    ).map(v)

    protected fun convertPostfixUnaryExpression(v: KtPostfixExpression) = Node.Expression.PostfixUnaryExpression(
        expression = convertExpression(v.baseExpression ?: error("No base expression for $v")),
        operator = convertKeyword(v.operationReference),
    ).map(v)

    protected fun convertBinaryTypeExpression(v: KtBinaryExpressionWithTypeRHS) = Node.Expression.BinaryTypeExpression(
        lhs = convertExpression(v.left),
        operator = convertKeyword(v.operationReference),
        rhs = convertType(v.right ?: error("No type op rhs for $v"))
    ).map(v)

    protected fun convertBinaryTypeExpression(v: KtIsExpression) = Node.Expression.BinaryTypeExpression(
        lhs = convertExpression(v.leftHandSide),
        operator = convertKeyword(v.operationReference),
        rhs = convertType(v.typeReference ?: error("No type op rhs for $v"))
    ).map(v)

    protected fun convertCallableReferenceExpression(v: KtCallableReferenceExpression) =
        Node.Expression.CallableReferenceExpression(
            lhs = v.receiverExpression?.let(::convertExpression),
            questionMarks = v.questionMarks.map(::convertKeyword),
            rhs = convertNameExpression(v.callableReference)
        ).map(v)

    protected fun convertClassLiteralExpression(v: KtClassLiteralExpression) = Node.Expression.ClassLiteralExpression(
        lhs = v.receiverExpression?.let(::convertExpression),
        questionMarks = v.questionMarks.map(::convertKeyword),
    ).map(v)

    protected fun convertParenthesizedExpression(v: KtParenthesizedExpression) =
        Node.Expression.ParenthesizedExpression(
            innerExpression = convertExpression(v.expression ?: error("No expression for $v"))
        ).map(v)

    protected fun convertStringLiteralExpression(v: KtStringTemplateExpression) =
        Node.Expression.StringLiteralExpression(
            entries = v.entries.map(::convertStringEntry),
            raw = v.text.startsWith("\"\"\"")
        ).map(v)

    protected fun convertStringEntry(v: KtStringTemplateEntry): Node.Expression.StringLiteralExpression.StringEntry =
        when (v) {
            is KtLiteralStringTemplateEntry -> convertLiteralStringEntry(v)
            is KtEscapeStringTemplateEntry -> convertEscapeStringEntry(v)
            is KtStringTemplateEntryWithExpression -> convertTemplateStringEntry(v)
            else -> error("Unrecognized string template type for $v")
        }

    protected fun convertLiteralStringEntry(v: KtLiteralStringTemplateEntry) =
        Node.Expression.StringLiteralExpression.LiteralStringEntry(
            text = v.text,
        ).map(v)

    protected fun convertEscapeStringEntry(v: KtEscapeStringTemplateEntry) =
        Node.Expression.StringLiteralExpression.EscapeStringEntry(
            text = v.text,
        ).map(v)

    protected fun convertTemplateStringEntry(v: KtStringTemplateEntryWithExpression) =
        Node.Expression.StringLiteralExpression.TemplateStringEntry(
            expression = convertExpression(v.expression ?: error("No expr tmpl")),
            short = v is KtSimpleNameStringTemplateEntry,
        ).map(v)

    protected fun convertConstantLiteralExpression(v: KtConstantExpression): Node.Expression.ConstantLiteralExpression =
        when (v.node.elementType) {
            KtNodeTypes.BOOLEAN_CONSTANT -> Node.Expression.BooleanLiteralExpression(v.text)
            KtNodeTypes.CHARACTER_CONSTANT -> Node.Expression.CharacterLiteralExpression(v.text)
            KtNodeTypes.INTEGER_CONSTANT -> Node.Expression.IntegerLiteralExpression(v.text)
            KtNodeTypes.FLOAT_CONSTANT -> Node.Expression.RealLiteralExpression(v.text)
            KtNodeTypes.NULL -> Node.Expression.NullLiteralExpression()
            else -> error("Unrecognized constant type for $v")
        }.map(v)

    protected fun convertObjectLiteralExpression(v: KtObjectLiteralExpression) =
        Node.Expression.ObjectLiteralExpression(
            declaration = convertClassDeclaration(v.objectDeclaration),
        ).map(v)

    protected fun convertCollectionLiteralExpression(v: KtCollectionLiteralExpression) =
        Node.Expression.CollectionLiteralExpression(
            expressions = v.getInnerExpressions().map(::convertExpression),
        ).map(v)

    protected fun convertThisExpression(v: KtThisExpression) = Node.Expression.ThisExpression(
        label = v.getTargetLabel()?.let(::convertNameExpression),
    ).map(v)

    protected fun convertSuperExpression(v: KtSuperExpression) = Node.Expression.SuperExpression(
        typeArgType = v.superTypeQualifier?.let(::convertType),
        label = v.getTargetLabel()?.let(::convertNameExpression),
    ).map(v)

    protected fun convertThisOrSuperExpression(v: KtConstructorDelegationReferenceExpression): Node.Expression =
        when (v.text) {
            "this" -> Node.Expression.ThisExpression(
                label = null,
            ).map(v)
            "super" -> Node.Expression.SuperExpression(
                typeArgType = null,
                label = null,
            ).map(v)
            else -> error("Unrecognized this/super expr $v")
        }

    protected fun convertNameExpression(v: KtSimpleNameExpression) = Node.Expression.NameExpression(
        text = (v.getIdentifier() ?: error("No identifier for $v")).text,
    ).map(v)

    protected fun convertNameExpression(v: PsiElement) = Node.Expression.NameExpression(
        text = v.text
    ).map(v)

    protected fun convertLabeledExpression(v: KtLabeledExpression) = Node.Expression.LabeledExpression(
        label = convertNameExpression(v.getTargetLabel() ?: error("No label name for $v")),
        statement = convertStatement(v.baseExpression ?: error("No label expr for $v"))
    ).map(v)

    protected fun convertAnnotatedExpression(v: KtAnnotatedExpression) = Node.Expression.AnnotatedExpression(
        annotationSets = convertAnnotationSets(v),
        statement = convertStatement(v.baseExpression ?: error("No annotated expr for $v"))
    ).map(v)

    protected fun convertIndexedAccessExpression(v: KtArrayAccessExpression) = Node.Expression.IndexedAccessExpression(
        expression = convertExpression(v.arrayExpression ?: error("No array expr for $v")),
        indices = v.indexExpressions.map(::convertExpression),
    ).map(v)

    protected fun convertAnonymousFunctionExpression(v: KtNamedFunction) = Node.Expression.AnonymousFunctionExpression(
        function = convertFunctionDeclaration(v),
    ).map(v)

    protected fun convertTypeParams(v: KtTypeParameterList?): List<Node.TypeParam> =
        v?.parameters.orEmpty().map(::convertTypeParam)

    protected fun convertTypeParam(v: KtTypeParameter) = Node.TypeParam(
        modifiers = convertModifiers(v.modifierList),
        name = v.nameIdentifier?.let(::convertNameExpression) ?: error("No type param name for $v"),
        type = v.extendsBound?.let(::convertType)
    ).map(v)

    protected fun convertFuncParams(v: KtParameterList?): List<Node.FunctionParam> =
        v?.parameters.orEmpty().map(::convertFuncParam)

    protected fun convertFuncParam(v: KtParameter) = Node.FunctionParam(
        modifiers = convertModifiers(v.modifierList),
        valOrVarKeyword = v.valOrVarKeyword?.let(::convertKeyword),
        name = v.nameIdentifier?.let(::convertNameExpression) ?: error("No param name"),
        type = v.typeReference?.let(::convertType),
        equals = v.equalsToken?.let(::convertKeyword),
        defaultValue = v.defaultValue?.let(::convertExpression),
    ).map(v)

    protected fun convertLambdaParams(v: KtParameterList?): List<Node.LambdaParam> =
        v?.parameters.orEmpty().map(::convertLambdaParam)

    protected fun convertLambdaParam(v: KtParameter): Node.LambdaParam {
        val destructuringDeclaration = v.destructuringDeclaration
        return if (destructuringDeclaration != null) {
            Node.LambdaParam(
                lPar = destructuringDeclaration.lPar?.let(::convertKeyword),
                variables = destructuringDeclaration.entries.map(::convertVariable),
                rPar = destructuringDeclaration.rPar?.let(::convertKeyword),
                destructType = v.typeReference?.let(::convertType),
            ).map(v)
        } else {
            Node.LambdaParam(
                lPar = null,
                variables = listOf(convertVariable(v)),
                rPar = null,
                destructType = null,
            ).map(v)
        }
    }

    protected fun convertVariable(v: KtDestructuringDeclarationEntry) = Node.Variable(
        annotationSets = convertAnnotationSets(v.modifierList),
        name = v.nameIdentifier?.let(::convertNameExpression) ?: error("No property name on $v"),
        type = v.typeReference?.let(::convertType)
    ).map(v)

    protected fun convertVariable(v: KtProperty) = Node.Variable(
        annotationSets = listOf(), // Annotations immediately before the name is not allowed.
        name = v.nameIdentifier?.let(::convertNameExpression) ?: error("No property name on $v"),
        type = v.typeReference?.let(::convertType)
    ).map(v)

    protected fun convertVariable(v: KtParameter) = Node.Variable(
        annotationSets = convertAnnotationSets(v.modifierList),
        name = v.nameIdentifier?.let(::convertNameExpression) ?: error("No lambda param name on $v"),
        type = v.typeReference?.let(::convertType),
    ).map(v)

    protected fun convertTypeArgs(v: KtTypeArgumentList?): List<Node.TypeArg> =
        v?.arguments.orEmpty().map(::convertTypeArg)

    protected fun convertTypeArg(v: KtTypeProjection) = Node.TypeArg(
        modifiers = convertModifiers(v.modifierList),
        type = when (v.projectionKind) {
            KtProjectionKind.STAR -> convertSimpleType(v)
            else -> convertType(v.typeReference ?: error("Missing type ref for $v"))
        },
    ).map(v)

    protected fun convertSimpleType(v: KtTypeProjection) = Node.Type.SimpleType(
        modifiers = listOf(),
        pieces = listOf(convertSimpleTypePiece(v.projectionToken ?: error("Missing projection token for $v"))),
    ).map(v)

    protected fun convertValueArgs(v: KtValueArgumentList?): List<Node.ValueArg> =
        v?.arguments.orEmpty().map(::convertValueArg)

    protected fun convertValueArg(v: KtValueArgument) = Node.ValueArg(
        name = v.getArgumentName()?.referenceExpression?.let(::convertNameExpression),
        asterisk = v.getSpreadElement()?.let(::convertKeyword),
        expression = convertExpression(v.getArgumentExpression() ?: error("No expr for value arg"))
    ).map(v)

    protected fun convertContextReceiver(v: KtContextReceiverList) = Node.ContextReceiver(
        lPar = convertKeyword(v.leftParenthesis),
        receiverTypes = v.contextReceivers().map { convertType(it.typeReference() ?: error("No type ref for $it")) },
        rPar = convertKeyword(v.rightParenthesis),
    ).map(v)

    protected fun convertModifiers(v: KtModifierList?): List<Node.Modifier> {
        return v?.nonExtraChildren().orEmpty().map { element ->
            when (element) {
                is KtAnnotationEntry -> convertAnnotationSet(element)
                is KtAnnotation -> convertAnnotationSet(element)
                else -> convertKeyword<Node.Modifier.KeywordModifier>(element)
            }
        }
    }

    protected fun convertAnnotationSets(v: KtAnnotationsContainer?): List<Node.Modifier.AnnotationSet> {
        return v?.children.orEmpty().mapNotNull { element ->
            when (element) {
                is KtAnnotationEntry -> convertAnnotationSet(element)
                is KtAnnotation -> convertAnnotationSet(element)
                else -> null
            }
        }
    }

    protected fun convertAnnotationSet(v: KtAnnotation) = Node.Modifier.AnnotationSet(
        target = v.useSiteTarget?.let(::convertKeyword),
        lBracket = v.lBracket?.let(::convertKeyword),
        annotations = v.entries.map(::convertAnnotation),
        rBracket = v.rBracket?.let(::convertKeyword),
    ).map(v)

    protected fun convertAnnotationSet(v: KtAnnotationEntry) = Node.Modifier.AnnotationSet(
        target = v.useSiteTarget?.let(::convertKeyword),
        lBracket = null,
        annotations = listOf(convertAnnotation(v)),
        rBracket = null,
    ).map(v)

    protected fun convertAnnotation(v: KtAnnotationEntry) = Node.Modifier.AnnotationSet.Annotation(
        type = convertType(
            v.calleeExpression?.typeReference ?: error("No callee expression, type reference or type element for $v")
        ) as? Node.Type.SimpleType ?: error("calleeExpression is not simple type"),
        lPar = v.valueArgumentList?.leftParenthesis?.let(::convertKeyword),
        args = convertValueArgs(v.valueArgumentList),
        rPar = v.valueArgumentList?.rightParenthesis?.let(::convertKeyword),
    ).map(v)

    protected fun convertPostModifiers(v: KtElement): List<Node.PostModifier> {
        return v.nonExtraChildren().drop(1).mapNotNull { psi ->
            when (psi) {
                is KtTypeConstraintList -> convertTypeConstraintSet(v, psi)
                is KtContractEffectList -> convertContract(v, psi)
                else -> null
            }
        }
    }

    protected fun convertTypeConstraintSet(v: KtElement, listEl: KtTypeConstraintList) =
        Node.PostModifier.TypeConstraintSet(
            constraints = convertTypeConstraints(listEl),
        ).map(v)

    protected fun convertTypeConstraints(v: KtTypeConstraintList): List<Node.PostModifier.TypeConstraintSet.TypeConstraint> =
        v.constraints.map(::convertTypeConstraint)

    protected fun convertTypeConstraint(v: KtTypeConstraint) = Node.PostModifier.TypeConstraintSet.TypeConstraint(
        annotationSets = v.children.mapNotNull {
            when (it) {
                is KtAnnotationEntry -> convertAnnotationSet(it)
                is KtAnnotation -> convertAnnotationSet(it)
                else -> null
            }
        },
        name = v.subjectTypeParameterName?.let { convertNameExpression(it) } ?: error("No type constraint name for $v"),
        type = convertType(v.boundTypeReference ?: error("No type constraint type for $v"))
    ).map(v)

    protected fun convertContract(v: KtElement, listEl: KtContractEffectList) = Node.PostModifier.Contract(
        lBracket = convertKeyword(listEl.leftBracket),
        contractEffects = convertContractEffects(listEl),
        rBracket = convertKeyword(listEl.rightBracket),
    ).map(v)

    protected fun convertContractEffects(v: KtContractEffectList): List<Node.Expression> =
        v.children.filterIsInstance<KtContractEffect>().map { convertExpression(it.getExpression()) }

    protected val mapTextToKeywordKClass =
        Node.Keyword::class.sealedSubclasses.filter { it.isData }.associateBy { it.createInstance().text }

    protected inline fun <reified T : Node.Keyword> convertKeyword(v: PsiElement): T =
        ((mapTextToKeywordKClass[v.text]?.createInstance() as? T) ?: error("Unexpected keyword: ${v.text}"))
            .map(v)

    /**
     * Map AST node to PSI element.
     *
     * You should map single node to only one PSI element.
     * You can map two or more nodes to one PSI element.
     * All children of the node must be descendants of the PSI element.
     */
    protected open fun <T : Node> T.map(v: PsiElement) = also { onNode(it, v) }

    class Unsupported(message: String) : UnsupportedOperationException(message)
}