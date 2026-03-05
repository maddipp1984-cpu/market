package de.projekt.timeseries.rest;

import de.projekt.timeseries.rest.dto.SidebarNodeDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @GetMapping("/sidebar")
    public List<SidebarNodeDto> getSidebarConfig() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream is = new ClassPathResource("sidebar.xml").getInputStream()) {
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();
            return parseChildren(doc.getDocumentElement());
        }
    }

    private List<SidebarNodeDto> parseChildren(Element parent) {
        List<SidebarNodeDto> nodes = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element el = (Element) node;
            String tagName = el.getTagName();

            if ("folder".equals(tagName)) {
                String id = el.getAttribute("id");
                String label = el.getAttribute("label");
                List<SidebarNodeDto> children = parseChildren(el);
                nodes.add(new SidebarNodeDto(id, label, null, children));
            } else if ("item".equals(tagName)) {
                String id = el.getAttribute("id");
                String tabType = el.getAttribute("tabType");
                nodes.add(new SidebarNodeDto(id, null, tabType, null));
            }
        }
        return nodes;
    }
}
