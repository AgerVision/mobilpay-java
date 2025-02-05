package ro.mobilPay.payment;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the functionality provided by Mobilpay_Payment_Split in PHP.
 * It loads the destination splits from XML and can generate XML elements for each destination.
 */
public class Split {

    // Error code constants (mimicking the PHP error codes)
    public static final int ERROR_INVALID_PARAMETER = 0x11110001;
    public static final int ERROR_INVALID_INTERVAL_DAY = 0x11110002;
    public static final int ERROR_INVALID_PAYMENTS_NO = 0x11110003;
    public static final int ERROR_LOAD_FROM_XML_CURRENCY_ATTR_MISSING = 0x31110001;

    // Holds the split destinations
    protected ArrayList<Destination> _destinations;

    /**
     * Default constructor initializes an empty list of destinations.
     */
    public Split() {
        _destinations = new ArrayList<>();
    }

    /**
     * Constructor that loads the split details from the provided XML element.
     *
     * @param elem The XML element containing the split payment details.
     * @throws Exception If a required attribute is missing.
     */
    public Split(Element elem) throws Exception {
        this();
        if (elem != null) {
            loadFromXml(elem);
        }
    }

    /**
     * Reads the <destination> nodes from the provided XML element.
     * The method mimics the PHP behavior by processing destinations only if the node list length is greater than 1.
     *
     * @param elem The XML element.
     * @return true if processing was successful.
     * @throws Exception If a destination is missing the "id" or "amount" attribute.
     */
    protected boolean loadFromXml(Element elem) throws Exception {
        // Initialize the destinations list
        _destinations = new ArrayList<>();

        NodeList destinationNodes = elem.getElementsByTagName("destination");

        for (int i = 0; i < destinationNodes.getLength(); i++) {
            Node destinationNode = destinationNodes.item(i);
            if (destinationNode.getNodeType() == Node.ELEMENT_NODE) {
                Element destinationElement = (Element) destinationNode;

                Node idAttr = destinationElement.getAttributes().getNamedItem("id");
                if (idAttr == null) {
                    throw new Exception("Mobilpay_Payment_Recurrence::loadFromXml failed; split id attribute missing " 
                                            + ERROR_LOAD_FROM_XML_CURRENCY_ATTR_MISSING);
                }

                Node amountAttr = destinationElement.getAttributes().getNamedItem("amount");
                if (amountAttr == null) {
                    throw new Exception("Mobilpay_Payment_Recurrence::loadFromXml failed; split amount attribute missing " 
                                            + ERROR_LOAD_FROM_XML_CURRENCY_ATTR_MISSING);
                }

                String id = idAttr.getNodeValue();
                String amount = amountAttr.getNodeValue();

                Destination destination = new Destination(id, amount);
                _destinations.add(destination);
            }
        }
        
        return true;
    }

    /**
     * Creates an array of XML elements corresponding to each split destination.
     *
     * @param xmlDoc The XML document used to create the XML elements.
     * @return An array of Elements, each representing a destination.
     * @throws Exception If the provided xmlDoc is null.
     */
    public Element[] createXMLElement(Document xmlDoc) throws Exception {
        if (xmlDoc == null) {
            throw new Exception("" + ERROR_INVALID_PARAMETER);
        }

        List<Element> retElems = new ArrayList<>();
        for (Destination destination : _destinations) {
            Element destElem = xmlDoc.createElement("destination");
            destElem.setAttribute("id", destination.getId());
            destElem.setAttribute("amount", destination.getAmount());
            retElems.add(destElem);
        }

        return retElems.toArray(new Element[0]);
    }

    /**
     * Optional helper method to add a destination manually.
     *
     * @param id     The destination ID.
     * @param amount The destination amount.
     */
    public void addDestination(String id, String amount) {
        if (_destinations == null) {
            _destinations = new ArrayList<>();
        }
        _destinations.add(new Destination(id, amount));
    }

    /**
     * Optional getter for the destinations list.
     *
     * @return The list of destinations.
     */
    public ArrayList<Destination> getDestinations() {
        return _destinations;
    }

    /**
     * Inner class representing a payment destination.
     */
    public static class Destination {
        private String id;
        private String amount;

        public Destination(String id, String amount) {
            this.id = id;
            this.amount = amount;
        }

        public String getId() {
            return id;
        }

        public String getAmount() {
            return amount;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }
    }
}
