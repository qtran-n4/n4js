/**
 * Copyright (c) 2016 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.ui.wizard.components;

import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import com.google.common.base.Predicate;

import org.eclipse.n4js.ui.wizard.model.ClassifierReference;

/**
 * Converters for component data binding.
 */
public class WizardComponentDataConverters {

	private static abstract class StrategyProvidingConverter extends Converter {
		/**
		 * @param fromType
		 *            source type
		 * @param toType
		 *            target type
		 */
		public StrategyProvidingConverter(Object fromType, Object toType) {
			super(fromType, toType);
		}

		public UpdateValueStrategy updatingValueStrategy() {
			UpdateValueStrategy strat = new UpdateValueStrategy(UpdateValueStrategy.POLICY_UPDATE);
			strat.setConverter(this);
			return strat;
		}
	}

	/**
	 * Converts a String to an IPath
	 */
	public static class StringToPathConverter extends StrategyProvidingConverter {

		/**
		 *
		 */
		public StringToPathConverter() {
			super(String.class, IPath.class);
		}

		@Override
		public Object convert(Object fromObject) {
			if (fromObject instanceof String) {
				return new Path(((String) fromObject));
			}
			return null;
		}

	}

	/**
	 * Converts a String to a ClassifierReference leaving the URI field empty.
	 */
	public static class StringToClassifierReferenceConverter extends StrategyProvidingConverter {

		/**
		 *
		 */
		public StringToClassifierReferenceConverter() {
			super(String.class, ClassifierReference.class);
		}

		@Override
		public Object convert(Object fromObject) {
			if (fromObject instanceof String) {
				String str = (String) fromObject;
				return new ClassifierReference(DotPathUtils.frontDotSegments(str),
						DotPathUtils.lastDotSegment(str));
			}
			return null;
		}

	}

	/**
	 * Converts a ClassifierReference to a String using its absolute specifier.
	 */
	public static class ClassifierReferenceToStringConverter extends StrategyProvidingConverter {

		/***/
		public ClassifierReferenceToStringConverter() {
			super(ClassifierReference.class, String.class);
		}

		@Override
		public Object convert(Object fromObject) {
			if (fromObject instanceof ClassifierReference) {
				ClassifierReference ref = (ClassifierReference) fromObject;
				return ref.getFullSpecifier();
			}
			return null;
		}

	}

	/**
	 * Helper method which returns an update value strategy using a conditional converter which is evaluating the
	 * predicate
	 */
	public static UpdateValueStrategy strategyForPredicate(Predicate<Object> predicate) {
		return new ConditionalConverter() {

			@Override
			public boolean validate(Object object) {
				return predicate.apply(object);
			}

		}.updatingValueStrategy();
	}

	/**
	 * Converts an arbitrary object to a boolean value depending on the outcome of the abstract validate method.
	 */
	public static abstract class ConditionalConverter extends StrategyProvidingConverter {

		/**
		 */
		public ConditionalConverter() {
			super(Object.class, boolean.class);
			// TODO Auto-generated constructor stub
		}

		@Override
		public Object convert(Object fromObject) {
			if (validate(fromObject)) {
				return true;
			}
			return false;
		}

		/**
		 * Return true if condition is met
		 *
		 * @param object
		 *            Object to examine
		 * @return true on fulfillment
		 */
		public abstract boolean validate(Object object);

	}
}
