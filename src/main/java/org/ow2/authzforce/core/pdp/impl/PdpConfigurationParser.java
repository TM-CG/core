/**
 * Copyright (C) 2012-2017 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.core.pdp.impl;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;

import org.ow2.authzforce.core.pdp.api.DecisionResultFilter;
import org.ow2.authzforce.core.pdp.api.EnvironmentProperties;
import org.ow2.authzforce.core.pdp.api.EnvironmentPropertyName;
import org.ow2.authzforce.core.pdp.api.HashCollections;
import org.ow2.authzforce.core.pdp.api.combining.CombiningAlg;
import org.ow2.authzforce.core.pdp.api.combining.CombiningAlgRegistry;
import org.ow2.authzforce.core.pdp.api.func.FirstOrderFunction;
import org.ow2.authzforce.core.pdp.api.func.Function;
import org.ow2.authzforce.core.pdp.api.value.Datatype;
import org.ow2.authzforce.core.pdp.api.value.DatatypeFactory;
import org.ow2.authzforce.core.pdp.api.value.DatatypeFactoryRegistry;
import org.ow2.authzforce.core.pdp.impl.combining.ImmutableCombiningAlgRegistry;
import org.ow2.authzforce.core.pdp.impl.combining.StandardCombiningAlgorithm;
import org.ow2.authzforce.core.pdp.impl.func.FunctionRegistry;
import org.ow2.authzforce.core.pdp.impl.func.ImmutableFunctionRegistry;
import org.ow2.authzforce.core.pdp.impl.func.StandardFunction;
import org.ow2.authzforce.core.pdp.impl.value.ImmutableDatatypeFactoryRegistry;
import org.ow2.authzforce.core.pdp.impl.value.StandardDatatypeFactoryRegistry;
import org.ow2.authzforce.core.xmlns.pdp.Pdp;
import org.ow2.authzforce.xacml.identifiers.XACMLDatatypeId;
import org.ow2.authzforce.xmlns.pdp.ext.AbstractDecisionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;

/**
 * XML-based PDP Configuration parser
 *
 * @version $Id: $
 */
public final class PdpConfigurationParser
{
	private static final IllegalArgumentException NULL_PDP_MODEL_HANDLER_ARGUMENT_EXCEPTION = new IllegalArgumentException("Undefined PDP configuration model handler");
	private final static Logger LOGGER = LoggerFactory.getLogger(PdpConfigurationParser.class);

	/**
	 * Create PDP instance.
	 *
	 * @param confLocation
	 *            location of PDP configuration XML file, compliant with the PDP XML schema (pdp.xsd). This location may be any resource string supported by Spring ResourceLoader. For example:
	 *            classpath:com/myapp/aaa.xsd, file:///data/bbb.xsd, http://myserver/ccc.xsd... More info: http://docs.spring.io/spring/docs/current/spring-framework- reference/html/resources.html
	 * @return PDP instance
	 * @throws java.io.IOException
	 *             I/O error reading from {@code confLocation}
	 * @throws java.lang.IllegalArgumentException
	 *             Invalid PDP configuration at {@code confLocation}
	 */
	public static PDPImpl getPDP(final String confLocation) throws IOException, IllegalArgumentException
	{
		return getPDP(confLocation, null, null);
	}

	/**
	 * Create PDP instance. Locations here may be any resource string supported by Spring ResourceLoader. More info: http://docs.spring.io/spring/docs/current/spring-framework-reference/html
	 * /resources.html
	 *
	 * For example: classpath:com/myapp/aaa.xsd, file:///data/bbb.xsd, http://myserver/ccc.xsd...
	 *
	 * @param confLocation
	 *            location of PDP configuration XML file, compliant with the PDP XML schema (pdp.xsd)
	 * @param extensionXsdLocation
	 *            location of user-defined extension XSD (may be null if no extension to load), if exists; in such XSD, there must be a XSD namespace import for each extension used in the PDP
	 *            configuration, for example:
	 *
	 *            <pre>
	 * {@literal
	 * 		  <?xml version="1.0" encoding="UTF-8"?>
	 * <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
	 * 	<xs:annotation>
	 * 		<xs:documentation xml:lang="en">
	 * 			Import here the schema(s) of any XSD-defined PDP extension that you want to use in a PDP configuration: attribute finders, policy finders, etc.
	 * 			Indicate only the namespace here and use the XML catalog to resolve the schema location.
	 * 		</xs:documentation>
	 * 	</xs:annotation>
	 * 	<!-- Do not specify schema locations here. Define the schema locations in the XML catalog instead (see file 'catalog.xml'). -->
	 * 	<!--  Adding TestAttributeProvider extension for example -->
	 * 	<xs:import namespace="http://authzforce.github.io/core/xmlns/test/3" />
	 * </xs:schema>
	 * 			}
	 * </pre>
	 *
	 *            In this example, the file at {@code catalogLocation} must define the schemaLocation for the imported namespace above using a line like this (for an XML-formatted catalog):
	 * 
	 *            <pre>
	 *            {@literal
	 *            <uri name="http://authzforce.github.io/core/xmlns/test/3" uri=
	 * 	"classpath:org.ow2.authzforce.core.test.xsd" />
	 *            }
	 * </pre>
	 * 
	 *            We assume that this XML type is an extension of one the PDP extension base types, 'AbstractAttributeProvider' (that extends 'AbstractPdpExtension' like all other extension base
	 *            types) in this case.
	 * @param catalogLocation
	 *           location of XML catalog for resolving XSDs imported by the extension XSD specified as 'extensionXsdLocation' argument (may be null if 'extensionXsdLocation' is null)
	 * @return PDP instance
	 * @throws java.io.IOException
	 *             I/O error reading from {@code confLocation}
	 * @throws java.lang.IllegalArgumentException
	 *             Invalid PDP configuration at {@code confLocation}
	 */
	public static PDPImpl getPDP(final String confLocation, final String catalogLocation, final String extensionXsdLocation) throws IOException, IllegalArgumentException
	{
		return getPDP(confLocation, new PdpModelHandler(catalogLocation, extensionXsdLocation));
	}

	/**
	 * Create PDP instance. Locations here can be any resource string supported by Spring ResourceLoader. More info: http://docs.spring.io/spring/docs/current/spring-framework-reference/html
	 * /resources.html
	 *
	 * For example: classpath:com/myapp/aaa.xsd, file:///data/bbb.xsd, http://myserver/ccc.xsd...
	 *
	 * @param confFile
	 *            PDP configuration XML file, compliant with the PDP XML schema (pdp.xsd)
	 * @param extensionXsdLocation
	 *            location of user-defined extension XSD (may be null if no extension to load), if exists; in such XSD, there must be a XSD namespace import for each extension used in the PDP
	 *            configuration, for example:
	 *
	 *            <pre>
	 * {@literal
	 * 		  <?xml version="1.0" encoding="UTF-8"?>
	 * <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
	 * 	<xs:annotation>
	 * 		<xs:documentation xml:lang="en">
	 * 			Import here the schema(s) of any XSD-defined PDP extension that you want to use in a PDP configuration: attribute finders, policy finders, etc.
	 * 			Indicate only the namespace here and use the XML catalog to resolve the schema location.
	 * 		</xs:documentation>
	 * 	</xs:annotation>
	 * 	<!-- Do not specify schema locations here. Define the schema locations in the XML catalog instead (see file 'catalog.xml'). -->
	 * 	<!--  Adding TestAttributeProvider extension for example -->
	 * 	<xs:import namespace="http://authzforce.github.io/core/xmlns/test/3" />
	 * </xs:schema>
	 * 			}
	 * </pre>
	 *
	 *            In this example, the file at {@code catalogLocation} must define the schemaLocation for the imported namespace above using a line like this (for an XML-formatted catalog):
	 * 
	 *            <pre>
	 *            {@literal
	 *            <uri name="http://authzforce.github.io/core/xmlns/test/3" uri=
	 * 	"classpath:org.ow2.authzforce.core.test.xsd" />
	 *            }
	 * </pre>
	 * 
	 *            We assume that this XML type is an extension of one the PDP extension base types, 'AbstractAttributeProvider' (that extends 'AbstractPdpExtension' like all other extension base
	 *            types) in this case.
	 * @param catalogLocation
	 *            location of XML catalog for resolving XSDs imported by the extension XSD specified as 'extensionXsdLocation' argument (may be null if 'extensionXsdLocation' is null)
	 * @return PDP instance
	 * @throws java.io.IOException
	 *             I/O error reading from {@code confLocation}
	 * @throws java.lang.IllegalArgumentException
	 *             Invalid PDP configuration at {@code confLocation}
	 */
	public static PDPImpl getPDP(final File confFile, final String catalogLocation, final String extensionXsdLocation) throws IOException, IllegalArgumentException
	{
		return getPDP(confFile, new PdpModelHandler(catalogLocation, extensionXsdLocation));
	}

	/**
	 * Create PDP instance. Locations here can be any resource string supported by Spring ResourceLoader. More info: http://docs.spring.io/spring/docs/current/spring-framework-reference/html
	 * /resources.html.
	 * <p>
	 * To allow using file paths relative to the parent folder of the configuration file (located at confLocation) anywhere in this configuration file (including in PDP extensions'), we define a
	 * property 'PARENT_DIR', so that the placeholder ${PARENT_DIR} can be used as prefix for file paths in the configuration file. E.g. if confLocation = 'file:///path/to/configurationfile', then
	 * ${PARENT_DIR} will be replaced by 'file:///path/to'. If confLocation is not a file on the filesystem, then ${PARENT_DIR} is undefined.
	 *
	 * @param confLocation
	 *            location of PDP configuration file
	 * @param modelHandler
	 *            PDP configuration model handler
	 * @return PDP instance
	 * @throws java.io.IOException
	 *             I/O error reading from {@code confLocation}
	 * @throws java.lang.IllegalArgumentException
	 *             Invalid PDP configuration at {@code confLocation}
	 */
	public static PDPImpl getPDP(final String confLocation, final PdpModelHandler modelHandler) throws IOException, IllegalArgumentException
	{
		File confFile = null;
		try
		{
			confFile = ResourceUtils.getFile(confLocation);
		}
		catch (final FileNotFoundException e)
		{
			throw new IllegalArgumentException("Invalid PDP configuration location: " + confLocation, e);
		}

		return getPDP(confFile, modelHandler);
	}

	/**
	 * Create PDP instance
	 * <p>
	 * To allow using file paths relative to the parent folder of the configuration file (located at confLocation) anywhere in this configuration file (including in PDP extensions'), we define a
	 * property 'PARENT_DIR', so that the placeholder ${PARENT_DIR} can be used as prefix for file paths in the configuration file. E.g. if confLocation = 'file:///path/to/configurationfile', then
	 * ${PARENT_DIR} will be replaced by 'file:///path/to'. If confLocation is not a file on the filesystem, then ${PARENT_DIR} is undefined.
	 *
	 * @param confFile
	 *            PDP configuration file
	 * @param modelHandler
	 *            PDP configuration model handler
	 * @return PDP instance
	 * @throws java.io.IOException
	 *             I/O error reading from {@code confFile}
	 * @throws java.lang.IllegalArgumentException
	 *             Invalid PDP configuration in {@code confFile}
	 */
	public static PDPImpl getPDP(final File confFile, final PdpModelHandler modelHandler) throws IOException, IllegalArgumentException
	{
		if (confFile == null || !confFile.exists())
		{
			// no property replacement of PARENT_DIR
			throw new IllegalArgumentException("Invalid configuration file location: No file exists at: " + confFile);
		}

		if (modelHandler == null)
		{
			throw NULL_PDP_MODEL_HANDLER_ARGUMENT_EXCEPTION;
		}

		// configuration file exists
		final Pdp pdpJaxbConf;
		try
		{
			pdpJaxbConf = modelHandler.unmarshal(new StreamSource(confFile), Pdp.class);
		}
		catch (final JAXBException e)
		{
			throw new IllegalArgumentException("Invalid PDP configuration file", e);
		}

		// Set property PARENT_DIR in environment properties for future
		// replacement in configuration strings by PDP extensions using file
		// paths
		final String propVal = confFile.getParentFile().toURI().toString();
		LOGGER.debug("Property {} = {}", EnvironmentPropertyName.PARENT_DIR, propVal);
		final EnvironmentProperties envProps = new DefaultEnvironmentProperties(Collections.singletonMap(EnvironmentPropertyName.PARENT_DIR, propVal));
		return getPDP(pdpJaxbConf, envProps);
	}

	/**
	 * Get PDP instance
	 *
	 * @param pdpJaxbConf
	 *            (JAXB-bound) PDP configuration
	 * @param envProps
	 *            PDP configuration environment properties (e.g. PARENT_DIR)
	 * @return PDP instance
	 * @throws java.lang.IllegalArgumentException
	 *             invalid PDP configuration
	 * @throws java.io.IOException
	 *             if any error occurred closing already created {@link Closeable} modules (policy Providers, attribute Providers, decision cache)
	 */
	public static PDPImpl getPDP(final Pdp pdpJaxbConf, final EnvironmentProperties envProps) throws IllegalArgumentException, IOException
	{
		/*
		 * Initialize all parameters of ExpressionFactoryImpl: attribute datatype factories, functions, etc.
		 */

		final boolean enableXPath = pdpJaxbConf.isEnableXPath();

		// Attribute datatypes
		// Extensions
		final List<String> datatypeExtensionIdentifiers = pdpJaxbConf.getAttributeDatatypes();
		final Set<DatatypeFactory<?>> datatypeExtensions = HashCollections.newUpdatableSet(datatypeExtensionIdentifiers.size());
		for (final String datatypeId : datatypeExtensionIdentifiers)
		{
			final DatatypeFactory<?> datatypeFactory = PdpExtensionLoader.getExtension(DatatypeFactory.class, datatypeId);
			datatypeExtensions.add(datatypeFactory);
		}

		/*
		 * Merge with standards if required, or use the standards as is if no extension
		 */
		final DatatypeFactoryRegistry datatypeFactoryRegistry;
		if (pdpJaxbConf.isUseStandardDatatypes())
		{
			final DatatypeFactoryRegistry stdRegistry = StandardDatatypeFactoryRegistry.getRegistry(enableXPath);
			if (datatypeExtensionIdentifiers.isEmpty())
			{
				datatypeFactoryRegistry = stdRegistry;
			}
			else
			{
				datatypeFactoryRegistry = new ImmutableDatatypeFactoryRegistry(HashCollections.newImmutableSet(stdRegistry.getExtensions(), datatypeExtensions));
			}
		}
		else
		{
			datatypeFactoryRegistry = new ImmutableDatatypeFactoryRegistry(datatypeExtensions);
		}

		// Functions
		// Extensions (only non-generic functions supported in configuration)
		final List<String> nonGenericFunctionExtensionIdentifiers = pdpJaxbConf.getFunctions();
		final Set<Function<?>> nonGenericFunctionExtensions = HashCollections.newUpdatableSet(nonGenericFunctionExtensionIdentifiers.size());
		for (final String funcId : nonGenericFunctionExtensionIdentifiers)
		{
			final Function<?> function = PdpExtensionLoader.getExtension(Function.class, funcId);
			if (!enableXPath && isXpathBased(function))
			{
				throw new IllegalArgumentException("XPath-based function not allowed (because configuration parameter 'enableXPath' = false): " + function);
			}

			nonGenericFunctionExtensions.add(function);
		}

		/*
		 * Merge with standards if required, or use the standards as is if no extension
		 */
		final FunctionRegistry functionRegistry;
		if (pdpJaxbConf.isUseStandardFunctions())
		{
			final FunctionRegistry stdRegistry = StandardFunction.getRegistry(enableXPath);
			if (nonGenericFunctionExtensionIdentifiers.isEmpty())
			{
				functionRegistry = stdRegistry;
			}
			else
			{
				functionRegistry = new ImmutableFunctionRegistry(HashCollections.newImmutableSet(stdRegistry.getNonGenericFunctions(), nonGenericFunctionExtensions),
						stdRegistry.getGenericFunctionFactories());
			}
		}
		else
		{
			functionRegistry = new ImmutableFunctionRegistry(nonGenericFunctionExtensions, null);
		}

		// Combining Algorithms
		// Extensions
		final List<String> algExtensionIdentifiers = pdpJaxbConf.getCombiningAlgorithms();
		final Set<CombiningAlg<?>> algExtensions = HashCollections.newUpdatableSet(algExtensionIdentifiers.size());
		for (final String algId : algExtensionIdentifiers)
		{
			final CombiningAlg<?> alg = PdpExtensionLoader.getExtension(CombiningAlg.class, algId);
			algExtensions.add(alg);
		}

		/*
		 * Merge with standards if required, or use the standards as is if no extension
		 */
		final CombiningAlgRegistry combiningAlgRegistry;
		if (pdpJaxbConf.isUseStandardCombiningAlgorithms())
		{
			if (algExtensions.isEmpty())
			{
				combiningAlgRegistry = StandardCombiningAlgorithm.REGISTRY;
			}
			else
			{
				combiningAlgRegistry = new ImmutableCombiningAlgRegistry(HashCollections.newImmutableSet(StandardCombiningAlgorithm.REGISTRY.getExtensions(), algExtensions));
			}
		}
		else
		{
			combiningAlgRegistry = new ImmutableCombiningAlgRegistry(algExtensions);
		}

		// Decision combiner
		final String resultFilterId = pdpJaxbConf.getResultFilter();
		final DecisionResultFilter decisionResultFilter = resultFilterId == null ? null : PdpExtensionLoader.getExtension(DecisionResultFilter.class, resultFilterId);

		// decision cache
		final AbstractDecisionCache jaxbDecisionCache = pdpJaxbConf.getDecisionCache();

		final BigInteger bigMaxVarRefDepth = pdpJaxbConf.getMaxVariableRefDepth();
		final int maxVarRefDepth;
		try
		{
			maxVarRefDepth = bigMaxVarRefDepth == null ? -1 : bigMaxVarRefDepth.intValueExact();
		}
		catch (final ArithmeticException e)
		{
			throw new IllegalArgumentException("Invalid maxVariableRefDepth: " + bigMaxVarRefDepth, e);
		}

		final BigInteger bigMaxPolicyRefDepth = pdpJaxbConf.getMaxPolicyRefDepth();
		final int maxPolicyRefDepth;
		try
		{
			maxPolicyRefDepth = bigMaxPolicyRefDepth == null ? -1 : bigMaxPolicyRefDepth.intValueExact();
		}
		catch (final ArithmeticException e)
		{
			throw new IllegalArgumentException("Invalid maxPolicyRefDepth: " + bigMaxPolicyRefDepth, e);
		}

		return new PDPImpl(datatypeFactoryRegistry, functionRegistry, pdpJaxbConf.getAttributeProviders(), maxVarRefDepth, enableXPath, combiningAlgRegistry, pdpJaxbConf.getRootPolicyProvider(),
				pdpJaxbConf.getRefPolicyProvider(), maxPolicyRefDepth, pdpJaxbConf.getRequestFilter(), pdpJaxbConf.isStrictAttributeIssuerMatch(), pdpJaxbConf.getStandardEnvAttributeSource(),
				decisionResultFilter, jaxbDecisionCache, pdpJaxbConf.getBadRequestStatusDetailLevel().intValue(), envProps);
	}

	private static boolean isXpathBased(final Function<?> function)
	{
		/*
		 * A function is said "XPath-based" iff it takes at least one XPathExpression parameter. Regarding higher-order function, as of now, we only provide higher-order functions defined in the XACML
		 * (3.0) Core specification, which are not XPath-based, or if a higher-order function happens to take a XPathExpression parameter, it is actually a parameter to the first-order sub-function.
		 * Plus it is not possible to add extensions that are higher-order functions in this PDP implementation. Therefore, it is enough to check first-order functions (class FirstOrderFunction) only.
		 * (Remember that such functions may be used as parameter to a higher-order function.)
		 */
		if (function instanceof FirstOrderFunction)
		{
			final List<? extends Datatype<?>> paramTypes = ((FirstOrderFunction<?>) function).getParameterTypes();
			for (final Datatype<?> paramType : paramTypes)
			{
				if (paramType.getId().equals(XACMLDatatypeId.XPATH_EXPRESSION.value()))
				{
					return true;
				}
			}
		}

		return false;
	}

}
