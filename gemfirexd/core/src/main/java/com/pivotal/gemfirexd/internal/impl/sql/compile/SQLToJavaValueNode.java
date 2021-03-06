/*

   Derby - Class com.pivotal.gemfirexd.internal.impl.sql.compile.SQLToJavaValueNode

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

/*
 * Changes for GemFireXD distributed data platform (some marked by "GemStone changes")
 *
 * Portions Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package	com.pivotal.gemfirexd.internal.impl.sql.compile;









import com.pivotal.gemfirexd.internal.engine.distributed.metadata.ComparisonQueryInfo;
import com.pivotal.gemfirexd.internal.engine.distributed.metadata.QueryInfo;
import com.pivotal.gemfirexd.internal.engine.distributed.metadata.QueryInfoContext;
import com.pivotal.gemfirexd.internal.engine.sql.compile.ParameterizedConstantNode;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.reference.ClassName;
import com.pivotal.gemfirexd.internal.iapi.services.classfile.VMOpcode;
import com.pivotal.gemfirexd.internal.iapi.services.compiler.LocalField;
import com.pivotal.gemfirexd.internal.iapi.services.compiler.MethodBuilder;
import com.pivotal.gemfirexd.internal.iapi.services.sanity.SanityManager;
import com.pivotal.gemfirexd.internal.iapi.sql.Activation;
import com.pivotal.gemfirexd.internal.iapi.sql.compile.TypeCompiler;
import com.pivotal.gemfirexd.internal.iapi.sql.compile.Visitable;
import com.pivotal.gemfirexd.internal.iapi.sql.compile.Visitor;
import com.pivotal.gemfirexd.internal.iapi.sql.dictionary.DataDictionary;
import com.pivotal.gemfirexd.internal.iapi.types.DataTypeDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.JSQLType;
import com.pivotal.gemfirexd.internal.iapi.types.StringDataValue;
import com.pivotal.gemfirexd.internal.iapi.util.JBitSet;
import com.pivotal.gemfirexd.internal.impl.sql.compile.ExpressionClassBuilder;

import java.lang.reflect.Modifier;

import java.util.Vector;

/**
 * This node type converts a value in the SQL domain to a value in the Java
 * domain.
 */

public class SQLToJavaValueNode extends JavaValueNode
{
	ValueNode	value;

    /**
     * If set then this SQL value is being passed into a SQL function
     * declared RETURNS NULL ON NULL input. In this case this node
     * performs NULL checking logic in addition simple translation
     * from the SQL domain to the Java domain. Thus if this
     * is set then this node can not be removed when it
     * is paired with a JavaToSQLValueNode.
     * This field is set at generate time of the
     * enclosing StaticMethodCallNode.
     */
	LocalField	returnsNullOnNullState;

	/**
	 * Constructor for a SQLToJavaValueNode
	 *
	 * @param value		A ValueNode representing a SQL value to convert to
	 *					the Java domain.
	 */

	public void init(Object value)
	{
		this.value = (ValueNode) value;
	}

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 *
	 * @param depth		The depth of this node in the tree
	 */

	public void printSubNodes(int depth)
	{
		if (SanityManager.DEBUG)
		{
			int	parm;

			super.printSubNodes(depth);
			if (value != null)
			{
				printLabel(depth, "value: ");
				value.treePrint(depth + 1);
			}
		}
	}

	/**
	  *	Returns the name of the java class type that this node coerces to.
	  *
	  *	@return	name of java class type
	  *
	  */
	public String getJavaTypeName()
	throws StandardException
	{
		JSQLType	myType = getJSQLType();

		if ( myType == null ) { return ""; }
		else { return	mapToTypeID( myType ).getCorrespondingJavaTypeName(); }
	}

	/**
	  *	Returns the name of the java primitive type that this node coerces to.
	  *
	  *	@return	name of java primitive type
	  *
	  * @exception StandardException		Thrown on error
	  */
	public String getPrimitiveTypeName()
		throws StandardException
	{
		JSQLType	myType = getJSQLType();

		if ( myType == null )
		{
			return "";
		}
		else
		{
			return
				getTypeCompiler(mapToTypeID( myType )).
										getCorrespondingPrimitiveTypeName();
		}
	}

	/**
	  *	Get the JSQLType that corresponds to this node. Could be a SQLTYPE,
	  *	a Java primitive, or a Java class.
	  *
	  *	Overrides method in JavaValueNode.
	  *
	  *	@return	the corresponding JSQLType
	  *
	  */
	public	JSQLType	getJSQLType	() throws StandardException
	{
		if ( jsqlType == null )
		{
//	              GemStone changes begin
			if ( value.requiresTypeFromContext() ) 
			{
//		              GemStone changes end
  				ValueNode pn;
	  			if (value instanceof UnaryOperatorNode) 
	  				pn = ((UnaryOperatorNode)value).getParameterOperand();
	  			else
	  				pn = value;
				jsqlType = pn.isParameterizedConstantNode()? ((ParameterizedConstantNode)pn).getJSQLType() :((ParameterNode)pn).getJSQLType();
				
			//	GemStone changes end
			}
			else
			{
				DataTypeDescriptor dtd = value.getTypeServices();
				if (dtd != null)
					jsqlType = new JSQLType( dtd );
			}
		}

		return jsqlType;
	}


	/**
	 * Bind this expression.  This means binding the sub-expressions,
	 * as well as figuring out what the return type is for this expression.
	 *
	 * @param fromList		The FROM list for the query this
	 *				expression is in, for binding columns.
	 * @param subqueryList		The subquery list being built as we find
	 *							SubqueryNodes
	 * @param aggregateVector	The aggregate vector being built as we find AggregateNodes
	 *
	 * @return this	
	 *
	 * @exception StandardException		Thrown on error
	 */

	public JavaValueNode bindExpression(
		FromList fromList, SubqueryList subqueryList,
		Vector	aggregateVector) 
			throws StandardException
	{
		/* Bind the expression under us */
		value = value.bindExpression(fromList, subqueryList,
							  aggregateVector);

		return this;
	}

    /**
     * Override behavior in superclass.
     */
    public DataTypeDescriptor getDataType() throws StandardException
    {
        return value.getTypeServices();
    }

	/**
	 * Remap all ColumnReferences in this tree to be clones of the
	 * underlying expression.
	 *
	 * @return JavaValueNode			The remapped expression tree.
	 *
	 * @exception StandardException			Thrown on error
	 */
	public JavaValueNode remapColumnReferencesToExpressions()
		throws StandardException
	{
		value = value.remapColumnReferencesToExpressions();
		return this;
	}

	/**
	 * Categorize this predicate.  Initially, this means
	 * building a bit map of the referenced tables for each predicate.
	 * If the source of this ColumnReference (at the next underlying level) 
	 * is not a ColumnReference or a VirtualColumnNode then this predicate
	 * will not be pushed down.
	 *
	 * For example, in:
	 *		select * from (select 1 from s) a (x) where x = 1
	 * we will not push down x = 1.
	 * NOTE: It would be easy to handle the case of a constant, but if the
	 * inner SELECT returns an arbitrary expression, then we would have to copy
	 * that tree into the pushed predicate, and that tree could contain
	 * subqueries and method calls.
	 * RESOLVE - revisit this issue once we have views.
	 *
	 * @param referencedTabs	JBitSet with bit map of referenced FromTables
	 * @param simplePredsOnly	Whether or not to consider method
	 *							calls, field references and conditional nodes
	 *							when building bit map
	 *
	 * @return boolean		Whether or not source.expression is a ColumnReference
	 *						or a VirtualColumnNode.
	 *
	 * @exception StandardException			Thrown on error
	 */
	public boolean categorize(JBitSet referencedTabs, boolean simplePredsOnly)
		throws StandardException
	{
		return value.categorize(referencedTabs, simplePredsOnly);
	}

	/**
	 * Preprocess an expression tree.  We do a number of transformations
	 * here (including subqueries, IN lists, LIKE and BETWEEN) plus
	 * subquery flattening.
	 * NOTE: This is done before the outer ResultSetNode is preprocessed.
	 *
	 * @param	numTables			Number of tables in the DML Statement
	 * @param	outerFromList		FromList from outer query block
	 * @param	outerSubqueryList	SubqueryList from outer query block
	 * @param	outerPredicateList	PredicateList from outer query block
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void preprocess(int numTables,
							FromList outerFromList,
							SubqueryList outerSubqueryList,
							PredicateList outerPredicateList) 
							throws StandardException
	{
		value.preprocess(numTables,
						 outerFromList, outerSubqueryList,
						 outerPredicateList);
	}

	/**
	 * Return the variant type for the underlying expression.
	 * The variant type can be:
	 *		VARIANT				- variant within a scan
	 *							  (method calls and non-static field access)
	 *		SCAN_INVARIANT		- invariant within a scan
	 *							  (column references from outer tables)
	 *		QUERY_INVARIANT		- invariant within the life of a query
	 *							  (constant expressions)
	 *
	 * @return	The variant type for the underlying expression.
	 * @exception StandardException	thrown on error
	 */
	protected int getOrderableVariantType() throws StandardException
	{
		return value.getOrderableVariantType();
	}

	///////////////////////////////////////////////////////////////////////
	//
	//	CODE GENERATION METHODS
	//
	///////////////////////////////////////////////////////////////////////


	/**
	 * Generate code to get the Java value out of a SQL value.
	 *
	 * Every SQL type has a corresponding Java type.  The getObject() method
	 * on the SQL type gets the right Java type.
	 *
	 * The generated code will be:
	 *
	 * (<Java type name>) ((DataValueDescriptor)
	 *								<generated value>.getObject())
	 *
	 * where <Java type name> comes from the getCorrespondingJavaTypeName()
	 * method of the value's TypeId.
	 *
	 * @param acb	The ExpressionClassBuilder for the class being built
	 * @param mb	The method the expression will go into
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */

	public void generateExpression(ExpressionClassBuilder acb,
											MethodBuilder mb)
									throws StandardException
	{       
		/* Compile the expression under us */
		generateSQLValue( acb, mb );

		/* now cast the SQLValue to a Java value */
		generateJavaValue( acb, mb);
	}

	/**
	 * Generate the SQLvalue that this node wraps.
	 *
	 * @param acb	The ExpressionClassBuilder for the class being built
	 * @param mb	The method the expression will go into
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */

	private void generateSQLValue(ExpressionClassBuilder acb,
											MethodBuilder mb)
									throws StandardException
	{
		value.generateExpression(acb, mb);
	}

	/**
	 * Generate code to cast the SQLValue to a Java value.
	 *
	 *
	 * @param acb	The ExpressionClassBuilder for the class being built
	 * @param mbex	The method the expression will go into
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */

	private void generateJavaValue
	(
		ExpressionClassBuilder	acb,
		MethodBuilder mbex
    )
		throws StandardException
	{
		/* If this is a conversion to a primitive type, then call the
		 * appropriate method for getting the primitive value and
		 * cast it to the primitive type. 
		 * NOTE: We first call Activation.nullToPrimitiveTest(),
		 * which will throw a StandardException if the value is null
		 */
		if ( isPrimitiveType() || mustCastToPrimitive() )
		{
			String		primitiveTN = value.getTypeCompiler().getCorrespondingPrimitiveTypeName();

			/* Put the code to check if the object is null and to
			 * get the primitive value in a method call.  This is
			 * necessary because we are generating an expression here and
			 * cannot have multiple statements.
			 * The method call will take SQLValue as a parameter.
			 */
			String[] pd = new String[1];
			pd[0] = getSQLValueInterfaceName(); // parameter "param1"

			MethodBuilder	mb = acb.newGeneratedFun(primitiveTN, Modifier.PRIVATE, pd);

			mb.getParameter(0);

			if (returnsNullOnNullState != null)
			{
				generateReturnsNullOnNullCheck(mb);
			}
			else
			{
				mb.dup();
				mb.upCast(ClassName.DataValueDescriptor);
				mb.push(primitiveTN); 
				mb.callMethod(VMOpcode.INVOKESTATIC, ClassName.BaseActivation, "nullToPrimitiveTest", "void", 2);
			}

			// stack is dvd

			/* Generate the code to get the primitive value */
			mb.callMethod(VMOpcode.INVOKEINTERFACE, ClassName.DataValueDescriptor,
								value.getTypeCompiler().getPrimitiveMethodName(), primitiveTN, 0);

			mb.methodReturn();
			mb.complete();

			/* Generate the call to the new method, with the parameter */

			mbex.pushThis();
			mbex.swap(); // caller pushed out parameter
			mbex.callMethod(VMOpcode.INVOKEVIRTUAL, (String) null, mb.getName(), primitiveTN, 1);
		}
		else
		{
			if (returnsNullOnNullState != null)
				generateReturnsNullOnNullCheck(mbex);

			/* Call getObject() to get the right type of Java value */
			mbex.callMethod(VMOpcode.INVOKEINTERFACE, ClassName.DataValueDescriptor, "getObject",
										"java.lang.Object", 0);

			mbex.cast(value.getTypeId().getCorrespondingJavaTypeName());
		}
	}

	/**
		Generate the code for the returns Null on Null input check..
		Stack must contain the DataDescriptorValue.
	*/

	private void generateReturnsNullOnNullCheck(MethodBuilder mb)
	{
		mb.dup();
		mb.callMethod(VMOpcode.INVOKEINTERFACE, ClassName.Storable,
								"isNull", "boolean", 0);

		mb.conditionalIf();
		  mb.push(true);
		mb.startElseCode();
		  mb.getField(returnsNullOnNullState);
		mb.completeConditional();
		
		mb.setField(returnsNullOnNullState);
	}


	/**
	  *	Get the type name of the SQLValue we generate.
	  *
	  *	@return	name of interface corresponding to SQLValue
	  *
	  *
	  * @exception StandardException		Thrown on error
	  */
	private	String	getSQLValueInterfaceName()
		throws StandardException
	{
		return value.getTypeCompiler().interfaceName();
	}

	///////////////////////////////////////////////////////////////////////
	//
	//	OTHER VALUE NODE METHODS
	//
	///////////////////////////////////////////////////////////////////////


	/**
	 * Get the SQL ValueNode that is being converted to a JavaValueNode
	 *
	 * @return	The underlying SQL ValueNode
	 */
	//Gemstone changes Begin       
        public ValueNode getSQLValueNode()
        {
                return value;
        }
//Gemstone changes End
	/** @see ValueNode#getConstantValueAsObject 
	 *
	 * @exception StandardException		Thrown on error
	 */
	Object getConstantValueAsObject()
		throws StandardException
	{
		return value.getConstantValueAsObject();
	}

	/**
	 * Accept a visitor, and call v.visit()
	 * on child nodes as necessary.  
	 * 
	 * @param v the visitor
	 *
	 * @exception StandardException on error
	 */
	public Visitable accept(Visitor v) 
		throws StandardException
	{
		Visitable returnNode = v.visit(this);
	
		if (v.skipChildren(this))
		{
			return returnNode;
		}

		if (value != null && !v.stopTraversal())
		{
			value = (ValueNode)value.accept(v);
		}

		return returnNode;
	}
// GemStone changes BEGIN
  public JavaValueNode genExpressionOperands(
      ResultColumnList outerResultColumns, ResultColumn parentRC,
      boolean remapToNew) throws StandardException {
    if (parentRC != null) {
      outerResultColumns.addResultColumn(parentRC);
      return this;
    }
    this.value = this.value.genExpressionOperands(outerResultColumns, null,
        remapToNew);
    return this;
  }
// GemStone changes END
}
