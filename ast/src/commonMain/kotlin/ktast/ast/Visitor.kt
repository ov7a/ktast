package ktast.ast

open class Visitor {
    fun visit(v: Node) = visit(v, null)

    protected open fun visit(v: Node, parent: Node?) = v.run {
        when (this) {
            is Node.CommaSeparatedNodeList<*> -> {
                visitChildren(elements)
                visitChildren(trailingComma)
            }
            is Node.NodeList<*> -> {
                visitChildren(elements)
            }
            is Node.File -> {
                visitChildren(annotationSets)
                visitChildren(packageDirective)
                visitChildren(importDirectives)
                visitChildren(declarations)
            }
            is Node.Script -> {
                visitChildren(annotationSets)
                visitChildren(packageDirective)
                visitChildren(importDirectives)
                visitChildren(expressions)
            }
            is Node.PackageDirective -> {
                visitChildren(modifiers)
                visitChildren(packageKeyword)
                visitChildren(names)
            }
            is Node.ImportDirective -> {
                visitChildren(importKeyword)
                visitChildren(names)
                visitChildren(alias)
            }
            is Node.ImportDirective.Alias -> {
                visitChildren(name)
            }
            is Node.Declaration.Class -> {
                visitChildren(modifiers)
                visitChildren(declarationKeyword)
                visitChildren(name)
                visitChildren(typeParams)
                visitChildren(primaryConstructor)
                visitChildren(parents)
                visitChildren(typeConstraints)
                visitChildren(body)
            }
            is Node.Declaration.Class.Parents -> {
                visitChildren(items)
            }
            is Node.Declaration.Class.Parent.CallConstructor -> {
                visitChildren(type)
                visitChildren(typeArgs)
                visitChildren(args)
                visitChildren(lambda)
            }
            is Node.Declaration.Class.Parent.DelegatedType -> {
                visitChildren(type)
                visitChildren(byKeyword)
                visitChildren(expression)
            }
            is Node.Declaration.Class.Parent.Type -> {
                visitChildren(type)
            }
            is Node.Declaration.Class.PrimaryConstructor -> {
                visitChildren(modifiers)
                visitChildren(constructorKeyword)
                visitChildren(params)
            }
            is Node.Declaration.Class.Body -> {
                visitChildren(enumEntries)
                visitChildren(declarations)
            }
            is Node.Declaration.Init -> {
                visitChildren(modifiers)
                visitChildren(block)
            }
            is Node.Declaration.Func -> {
                visitChildren(modifiers)
                visitChildren(funKeyword)
                visitChildren(typeParams)
                visitChildren(receiverTypeRef)
                visitChildren(name)
                visitChildren(postTypeParams)
                visitChildren(params)
                visitChildren(typeRef)
                visitChildren(postMods)
                visitChildren(body)
            }
            is Node.Declaration.Func.Param -> {
                visitChildren(modifiers)
                visitChildren(valOrVar)
                visitChildren(name)
                visitChildren(typeRef)
                visitChildren(initializer)
            }
            is Node.Declaration.Func.Body.Block -> {
                visitChildren(block)
            }
            is Node.Declaration.Func.Body.Expr -> {
                visitChildren(equals)
                visitChildren(expression)
            }
            is Node.Declaration.Property -> {
                visitChildren(modifiers)
                visitChildren(valOrVar)
                visitChildren(typeParams)
                visitChildren(receiverTypeRef)
                visitChildren(variable)
                visitChildren(typeConstraints)
                visitChildren(initializer)
                visitChildren(delegate)
                visitChildren(accessors)
            }
            is Node.Declaration.Property.Delegate -> {
                visitChildren(byKeyword)
                visitChildren(expression)
            }
            is Node.Declaration.Property.Variable.Single -> {
                visitChildren(name)
                visitChildren(typeRef)
            }
            is Node.Declaration.Property.Variable.Multi -> {
                visitChildren(vars)
                visitChildren(trailingComma)
            }
            is Node.Declaration.Property.Accessor.Get -> {
                visitChildren(modifiers)
                visitChildren(getKeyword)
                visitChildren(typeRef)
                visitChildren(postMods)
                visitChildren(body)
            }
            is Node.Declaration.Property.Accessor.Set -> {
                visitChildren(modifiers)
                visitChildren(setKeyword)
                visitChildren(params)
                visitChildren(postMods)
                visitChildren(body)
            }
            is Node.Declaration.Property.Accessor.Params -> {
                visitChildren(elements)
                visitChildren(trailingComma)
            }
            is Node.Declaration.TypeAlias -> {
                visitChildren(modifiers)
                visitChildren(name)
                visitChildren(typeParams)
                visitChildren(typeRef)
            }
            is Node.Declaration.SecondaryConstructor -> {
                visitChildren(modifiers)
                visitChildren(constructorKeyword)
                visitChildren(params)
                visitChildren(delegationCall)
                visitChildren(block)
            }
            is Node.Declaration.SecondaryConstructor.DelegationCall -> {
                visitChildren(args)
            }
            is Node.EnumEntry -> {
                visitChildren(modifiers)
                visitChildren(name)
                visitChildren(args)
                visitChildren(body)
            }
            is Node.Initializer -> {
                visitChildren(equals)
                visitChildren(expression)
            }
            is Node.TypeParam -> {
                visitChildren(modifiers)
                visitChildren(name)
                visitChildren(typeRef)
            }
            is Node.TypeArg.Asterisk -> {
                visitChildren(asterisk)
            }
            is Node.TypeArg.Type -> {
                visitChildren(modifiers)
                visitChildren(typeRef)
            }
            is Node.TypeRef -> {
                visitChildren(lPar)
                visitChildren(modifiers)
                visitChildren(innerLPar)
                visitChildren(innerMods)
                visitChildren(type)
                visitChildren(innerRPar)
                visitChildren(rPar)
            }
            is Node.Type.Func -> {
                visitChildren(contextReceivers)
                visitChildren(receiver)
                visitChildren(params)
                visitChildren(typeRef)
            }
            is Node.Type.Func.ContextReceiver -> {
                visitChildren(typeRef)
            }
            is Node.Type.Func.Receiver -> {
                visitChildren(typeRef)
            }
            is Node.Type.Func.Param -> {
                visitChildren(name)
                visitChildren(typeRef)
            }
            is Node.Type.Simple -> {
                visitChildren(pieces)
            }
            is Node.Type.Simple.Piece -> {
                visitChildren(name)
                visitChildren(typeArgs)
            }
            is Node.Type.Nullable -> {
                visitChildren(lPar)
                visitChildren(modifiers)
                visitChildren(type)
                visitChildren(rPar)
            }
            is Node.Type.Dynamic -> {}
            is Node.ConstructorCallee -> {
                visitChildren(type)
            }
            is Node.ValueArg -> {
                visitChildren(name)
                visitChildren(expression)
            }
            is Node.Container -> {
                visitChildren(expression)
            }
            is Node.Expression.If -> {
                visitChildren(ifKeyword)
                visitChildren(condition)
                visitChildren(body)
                visitChildren(elseBody)
            }
            is Node.Expression.Try -> {
                visitChildren(block)
                visitChildren(catches)
                visitChildren(finallyBlock)
            }
            is Node.Expression.Try.Catch -> {
                visitChildren(catchKeyword)
                visitChildren(params)
                visitChildren(block)
            }
            is Node.Expression.For -> {
                visitChildren(forKeyword)
                visitChildren(annotationSets)
                visitChildren(loopParam)
                visitChildren(loopRange)
                visitChildren(body)
            }
            is Node.Expression.While -> {
                visitChildren(whileKeyword)
                visitChildren(condition)
                visitChildren(body)
            }
            is Node.Expression.BinaryOp -> {
                visitChildren(lhs)
                visitChildren(oper)
                visitChildren(rhs)
            }
            is Node.Expression.BinaryOp.Oper.Infix -> {}
            is Node.Expression.BinaryOp.Oper.Token -> {}
            is Node.Expression.UnaryOp -> {
                visitChildren(expression)
                visitChildren(oper)
            }
            is Node.Expression.UnaryOp.Oper -> {}
            is Node.Expression.TypeOp -> {
                visitChildren(lhs)
                visitChildren(oper)
                visitChildren(rhs)
            }
            is Node.Expression.TypeOp.Oper -> {}
            is Node.Expression.DoubleColonRef.Callable -> {
                visitChildren(recv)
                visitChildren(name)
            }
            is Node.Expression.DoubleColonRef.Class -> {
                visitChildren(recv)
            }
            is Node.Expression.DoubleColonRef.Recv.Expr -> {
                visitChildren(expression)
            }
            is Node.Expression.DoubleColonRef.Recv.Type -> {
                visitChildren(type)
                visitChildren(questionMarks)
            }
            is Node.Expression.Paren -> {
                visitChildren(expression)
            }
            is Node.Expression.StringTmpl -> {
                visitChildren(elems)
            }
            is Node.Expression.StringTmpl.Elem.Regular -> {}
            is Node.Expression.StringTmpl.Elem.ShortTmpl -> {}
            is Node.Expression.StringTmpl.Elem.UnicodeEsc -> {}
            is Node.Expression.StringTmpl.Elem.RegularEsc -> {}
            is Node.Expression.StringTmpl.Elem.LongTmpl -> {
                visitChildren(expression)
            }
            is Node.Expression.Const -> {}
            is Node.Expression.Lambda -> {
                visitChildren(params)
                visitChildren(body)
            }
            is Node.Expression.Lambda.Param.Single -> {
                visitChildren(name)
                visitChildren(typeRef)
            }
            is Node.Expression.Lambda.Param.Multi -> {
                visitChildren(vars)
                visitChildren(destructTypeRef)
            }
            is Node.Expression.Lambda.Body -> {
                visitChildren(statements)
            }
            is Node.Expression.This -> {}
            is Node.Expression.Super -> {
                visitChildren(typeArg)
            }
            is Node.Expression.When -> {
                visitChildren(lPar)
                visitChildren(expression)
                visitChildren(rPar)
                visitChildren(entries)
            }
            is Node.Expression.When.Entry.Conds -> {
                visitChildren(conds)
                visitChildren(trailingComma)
                visitChildren(body)
            }
            is Node.Expression.When.Entry.Else -> {
                visitChildren(elseKeyword)
                visitChildren(body)
            }
            is Node.Expression.When.Cond.Expr -> {
                visitChildren(expression)
            }
            is Node.Expression.When.Cond.In -> {
                visitChildren(expression)
            }
            is Node.Expression.When.Cond.Is -> {
                visitChildren(typeRef)
            }
            is Node.Expression.Object -> {
                visitChildren(declaration)
            }
            is Node.Expression.Throw -> {
                visitChildren(expression)
            }
            is Node.Expression.Return -> {
                visitChildren(expression)
            }
            is Node.Expression.Continue -> {}
            is Node.Expression.Break -> {}
            is Node.Expression.CollLit -> {
                visitChildren(expressions)
                visitChildren(trailingComma)
            }
            is Node.Expression.Name -> {}
            is Node.Expression.Labeled -> {
                visitChildren(expression)
            }
            is Node.Expression.Annotated -> {
                visitChildren(annotationSets)
                visitChildren(expression)
            }
            is Node.Expression.Call -> {
                visitChildren(expression)
                visitChildren(typeArgs)
                visitChildren(args)
                visitChildren(lambdaArgs)
            }
            is Node.Expression.Call.LambdaArg -> {
                visitChildren(annotationSets)
                visitChildren(func)
            }
            is Node.Expression.ArrayAccess -> {
                visitChildren(expression)
                visitChildren(indices)
                visitChildren(trailingComma)
            }
            is Node.Expression.AnonFunc -> {
                visitChildren(func)
            }
            is Node.Expression.Property -> {
                visitChildren(declaration)
            }
            is Node.Expression.Block -> {
                visitChildren(statements)
            }
            is Node.Modifier.AnnotationSet -> {
                visitChildren(atSymbol)
                visitChildren(lBracket)
                visitChildren(anns)
                visitChildren(rBracket)
            }
            is Node.Modifier.AnnotationSet.Annotation -> {
                visitChildren(constructorCallee)
                visitChildren(args)
            }
            is Node.Modifier.Lit -> {}
            is Node.PostModifier.TypeConstraints -> {
                visitChildren(whereKeyword)
                visitChildren(constraints)
            }
            is Node.PostModifier.TypeConstraints.TypeConstraint -> {
                visitChildren(annotationSets)
                visitChildren(name)
                visitChildren(typeRef)
            }
            is Node.PostModifier.Contract -> {
                visitChildren(contractKeyword)
                visitChildren(contractEffects)
            }
            is Node.PostModifier.Contract.ContractEffect -> {
                visitChildren(expression)
            }
            is Node.Keyword -> {}
            is Node.Extra -> {}
        }
    }

    protected fun <T : Node> Node.visitChildren(v: T?) {
        if (v != null) {
            visit(v, this)
        }
    }

    protected fun <T : Node> Node.visitChildren(v: List<T>) {
        v.forEach { orig -> visit(orig, this) }
    }

    companion object {
        fun visit(v: Node, fn: (v: Node, parent: Node?) -> Unit) = object : Visitor() {
            override fun visit(v: Node, parent: Node?) {
                fn(v, parent)
                super.visit(v, parent)
            }
        }.visit(v)
    }
}