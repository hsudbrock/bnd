package aQute.lib.xmldtoparser;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import aQute.lib.converter.Converter;

/**
 * Parse an XML file based on a DTO as schema
 */
public class DomDTOParser {
	final static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

	/**
	 * parse the given XML file based on the type as the schema. Attributes and
	 * elements are mapped to fields in an object of this type. If the field is
	 * a collection or a DTO type, the parse will be recursive.
	 * 
	 * @param type the type acting as scheme
	 * @param doc the file
	 * @return a DTO of type
	 */
	public static <T> T parse(Class<T> type, File doc) throws Exception {
		return parse(type, dbf.newDocumentBuilder().parse(doc));
	}

	/**
	 * parse the given XML file based on the type as the schema. Attributes and
	 * elements are mapped to fields in an object of this type. If the field is
	 * a collection or a DTO type, the parse will be recursive.
	 * 
	 * @param type the type acting as scheme
	 * @param doc the file
	 * @return a DTO of type
	 */
	public static <T> T parse(Class<T> type, InputStream doc) throws Exception {
		return parse(type, dbf.newDocumentBuilder().parse(doc));
	}

	private static <T> T parse(Class<T> type, Node node) throws Exception {
		T instance = type.newInstance();

		NamedNodeMap attributes = node.getAttributes();
		if (attributes != null) {
			for (int i = 0; i < attributes.getLength(); i++) {
				Attr attribute = (Attr) attributes.item(i);
				get(instance, attribute);
			}
		}
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			get(instance, child);
		}
		return instance;
	}

	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	private static <T> void get(T instance, Node node) throws Exception {
		if (node instanceof Comment)
			return;
		if (node instanceof Text) {
			String text = node.getTextContent()
				.trim();
			if (text.isEmpty())
				return;

			Field field = getField(instance.getClass(), "_content");
			if (field == null)
				return;

			field.set(instance, Converter.cnv(field.getGenericType(), text));
			return;
		}

		String name = toSimpleName(node.getNodeName());
		Field field = getField(instance.getClass(), name);
		if (field == null) {

			//
			// Store the content in an __extra variable
			// if exists for diagnostics
			//

			try {
				field = getField(instance.getClass(), "__extra");
				if (field != null && field.getType() == Map.class) {
					Map map = (Map) field.get(instance);
					if (map == null) {
						map = new HashMap();
						field.set(instance, map);
					}
					map.put(name, node.getTextContent());
				}
			} catch (Exception e) {
				// ignore, best effort
			}
			return;
		}

		if (isCollection(field.getType())) {

			ParameterizedType subType = (ParameterizedType) field.getGenericType();
			Type collectionType = subType.getActualTypeArguments()[0];
			Object member = parse((Class<T>) collectionType, node);
			Collection<Object> collection = (Collection<Object>) field.get(instance);
			collection.add(member);

		} else if (isSimple(field.getType())) {

			String value = node.getTextContent();
			Object convertedValue = Converter.cnv(field.getType(), value);
			field.set(instance, convertedValue);

		} else if (field.getType().isEnum()) {
			String value = node.getTextContent();
			Class< ? > en = field.getType();

			for (Field constant : en.getFields()) {
				String nm = constant.getName();
				if (nm.equalsIgnoreCase(value)) {
					field.set(instance, constant.get(null));
				}
			}

		} else {
			field.set(instance, parse(field.getType(), node));
		}

	}

	private static String toSimpleName(String nodeName) {
		int n = nodeName.indexOf(':');
		return nodeName.substring(n + 1);
	}

	private static boolean isSimple(Class< ? > class1) {
		return class1.isPrimitive() || Number.class.isAssignableFrom(class1) || class1 == Boolean.class
				|| class1 == String.class;
	}

	private static boolean isCollection(Class< ? > class1) {
		return Collection.class.isAssignableFrom(class1);
	}

	private static Field getField(Class< ? extends Object> class1, String name) throws Exception {
		try {
			return class1.getField(name);
		} catch (Exception e) {
			for (Field field : class1.getFields()) {
				XmlAttribute xmlAttribute = field.getAnnotation(XmlAttribute.class);
				if (xmlAttribute != null) {
					String annName = xmlAttribute.name();
					if (name.equals(annName)) {
						return field;
					}
				}
			}
		}
		return null;
	}

}
