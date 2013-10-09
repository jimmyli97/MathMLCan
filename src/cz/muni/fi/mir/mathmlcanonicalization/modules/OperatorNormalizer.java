package cz.muni.fi.mir.mathmlcanonicalization.modules;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jdom2.filter.ContentFilter;
import org.jdom2.filter.ElementFilter;

/**
 * Normalize the way to express an function applied to arguments in MathML.
 * <h4>Input</h4> Well-formed MathML, not processed by MrowMinimizer yet
 * <h4>Output</h4> The original code with:<ul><li>normalized Unicode symbols
 * </li><li>unified operators</li><li>no redundant operators</li></ul>
 *
 * @author David Formanek
 */
public class OperatorNormalizer extends AbstractModule implements DOMModule {
    
    /**
     * Path to the property file with module settings.
     */
    private static final String PROPERTIES_FILENAME = "/res/operator-normalizer.properties";
    private static final Logger LOGGER = Logger.getLogger(OperatorNormalizer.class.getName());
    
    // properties key names
    private static final String REMOVE_EMPTY_OPERATORS = "removeempty";
    private static final String OPERATORS_TO_REMOVE = "removeoperators";
    private static final String OPERATOR_REPLACEMENTS = "replaceoperators";
    private static final String COLON_REPLACEMENT = "colonreplacement";
    private static final String NORMALIZATION_FORM = "normalizationform";
    
    public OperatorNormalizer() {
        loadProperties(PROPERTIES_FILENAME);
    }
    
    @Override
    public void execute(final Document doc) {
        if (doc == null) {
            throw new NullPointerException("doc");
        }
        final Element root = doc.getRootElement();
        
        // TODO: convert Unicode superscripts (supX entities) to msup etc.
        
        final String normalizerFormStr = getProperty(NORMALIZATION_FORM);
        if (normalizerFormStr.isEmpty()) {
            LOGGER.fine("Unicode text normalization is switched off");
        } else {
            try {
                Normalizer.Form normalizerForm = Normalizer.Form.valueOf(normalizerFormStr);
                normalizeUnicode(root, normalizerForm);
            } catch(IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid configuration value: "
                        + NORMALIZATION_FORM, ex);
            }
        }
        
        if (isEnabled(REMOVE_EMPTY_OPERATORS) || !getProperty(OPERATORS_TO_REMOVE).isEmpty()) {
            removeSpareOperators(root, getPropertySet(OPERATORS_TO_REMOVE));
        } else {
            LOGGER.fine("No operators set for removal");
        }
        
        final Map<String, String> replaceMap = getPropertyMap(OPERATOR_REPLACEMENTS);
        if (!getProperty(COLON_REPLACEMENT).isEmpty()) {
            replaceMap.put(":", getProperty(COLON_REPLACEMENT));
        }
        if (replaceMap.isEmpty()) {
            LOGGER.fine("No operators set to replace");
        } else {
            replaceOperators(root, replaceMap);
        }
    }
    
    private void normalizeUnicode(final Element ancestor, final Normalizer.Form form) {
        assert ancestor != null && form != null;
        final List<Text> texts = new ArrayList<Text>();
        final ContentFilter textFilter = new ContentFilter(ContentFilter.TEXT);
        for (Content text : ancestor.getContent(textFilter)) {
            texts.add((Text) text);
        }
        for (Element element : ancestor.getDescendants(new ElementFilter())) {
            for (Content text : element.getContent(textFilter)) {
                texts.add((Text) text);
            }
        }
        for (Text text : texts) {
            if (Normalizer.isNormalized(text.getText(), form)) {
                continue;
            }
            final String normalizedString = Normalizer.normalize(text.getText(), form);
            LOGGER.log(Level.FINE, "Text ''{0}'' normalized to ''{1}''",
                    new Object[]{text.getText(), normalizedString});
            text.setText(normalizedString);
            assert Normalizer.isNormalized(text.getText(), form);
        }
    }
    
    private void removeSpareOperators(final Element element, final Collection<String> spareOperators) {
        assert element != null && spareOperators != null;
        final List<Element> children = element.getChildren();
        for (int i = 0; i < children.size(); i++) {
            final Element actual = children.get(i); // actual element
            if (isOperator(actual)) {
                if (isSpareOperator(actual, spareOperators)){
                    actual.detach();
                    i--; // move iterator back after detaching so it points to next element
                    LOGGER.log(Level.FINE, "Operator {0} removed", actual);
                }
            } else {
                removeSpareOperators(actual, spareOperators);
            }
        }
    }
    
    private boolean isSpareOperator(final Element operator, final Collection<String> spareOperators) {
        assert operator != null && spareOperators != null && isOperator(operator);
        return (isEnabled(REMOVE_EMPTY_OPERATORS) && operator.getText().isEmpty())
                || (spareOperators.contains(operator.getTextTrim()));
    }
    
    private void replaceOperators(final Element element, final Map<String,String> replacements) {
        assert element != null && replacements != null;
        List<Element> operatorsToReplace = new ArrayList<Element>();
        for (Element operator : element.getDescendants(new ElementFilter(OPERATOR))) {
            if (replacements.containsKey(operator.getTextTrim())) {
                operatorsToReplace.add(operator);
            }
        }
        for (Element operator : operatorsToReplace) {
            final String oldOperator = operator.getTextTrim();
            final String newOperator = replacements.get(oldOperator);
            operator.setText(newOperator);
            LOGGER.log(Level.FINE, "Operator ''{0}'' was replaced by ''{1}''",
                    new Object[]{oldOperator, newOperator});
        }
    }
    
    private Map<String,String> getPropertyMap(final String property) {
        assert property != null && isProperty(property);
        final Map<String,String> propertyMap = new HashMap<String,String>();
        final String[] mappings = getProperty(property).split(" ");
        for (int i = 0; i < mappings.length; i++) {
            final String[] mapping = mappings[i].split(":", 2);
            if (mapping.length != 2) {
                throw new IllegalArgumentException("property has wrong format");
            }
            propertyMap.put(mapping[0], mapping[1]);
        }
        return propertyMap;
    }
}
