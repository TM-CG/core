/**
 * Copyright (C) 2011-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.core.value;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This registry provides the factories for standard attribute datatypes specified in XACML.
 * <p>
 * Note that because this supports only the standard datatypes, this factory does not allow the addition of any other datatypes. If you call
 * <code>addDatatype</code> on an instance of this class, an exception will be thrown. If you need a standard factory that is modifiable, you should create a
 * new <code>BaseDatatypeFactoryRegistry</code> (or some other <code>DatatypeFactoryRegistry</code>) and pass this to
 * {@link BaseDatatypeFactoryRegistry#BaseDatatypeFactoryRegistry(BaseDatatypeFactoryRegistry)}.
 */
public final class StandardDatatypeFactoryRegistry extends BaseDatatypeFactoryRegistry
{
	// the LOGGER we'll use for all messages
	private static final Logger LOGGER = LoggerFactory.getLogger(StandardDatatypeFactoryRegistry.class);

	private static final PdpExtensionComparator<DatatypeFactory<?>> COMPARATOR = new PdpExtensionComparator<>();

	/**
	 * Singleton instance of standard datatype factory.
	 */
	public static final StandardDatatypeFactoryRegistry INSTANCE;

	static
	{
		final Set<DatatypeFactory<?>> datatypeFactories = new HashSet<>();
		for (final DatatypeConstants<?> datatypeDef : DatatypeConstants.SET)
		{
			datatypeFactories.add(datatypeDef.FACTORY);
		}

		INSTANCE = new StandardDatatypeFactoryRegistry(datatypeFactories);
		if (LOGGER.isDebugEnabled())
		{
			final TreeSet<DatatypeFactory<?>> sortedFactories = new TreeSet<>(COMPARATOR);
			sortedFactories.addAll(datatypeFactories);
			LOGGER.debug("Loaded XACML standard datatypes: {}", sortedFactories);
		}
	}

	/**
	 * Private constructor
	 * 
	 * @param stdDatatypeFactories
	 *            standard datatype factories
	 */
	private StandardDatatypeFactoryRegistry(Set<DatatypeFactory<?>> stdDatatypeFactories)
	{
		super(stdDatatypeFactories);
	}

	/**
	 * Throws an <code>UnsupportedOperationException</code> since you are not allowed to modify what a standard factory supports.
	 */
	@Override
	public void addExtension(DatatypeFactory<?> attrDatatypeFactory)
	{
		throw new UnsupportedOperationException("This factory does not support custom datatypes but only standard ones.");
	}

}
