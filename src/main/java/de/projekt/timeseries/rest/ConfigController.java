package de.projekt.timeseries.rest;

import de.projekt.timeseries.rest.dto.SidebarNodeDto;
import de.projekt.timeseries.security.PermissionService;
import de.projekt.timeseries.security.SecurityUtils;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Set<String> ADMIN_TAB_TYPES = Set.of("admin-users", "admin-groups", "admin-permissions");

    private final PermissionService permissionService;

    public ConfigController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/sidebar")
    public List<SidebarNodeDto> getSidebarConfig() throws Exception {
        UUID userId = UUID.fromString(SecurityUtils.getCurrentUserId());
        boolean isAdmin = permissionService.isAdmin(userId);
        Set<String> visibleKeys = permissionService.getVisibleResourceKeys(userId);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream is = new ClassPathResource("sidebar.xml").getInputStream()) {
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();
            return filterNodes(parseChildren(doc.getDocumentElement()), isAdmin, visibleKeys);
        }
    }

    private List<SidebarNodeDto> filterNodes(List<SidebarNodeDto> nodes, boolean isAdmin, Set<String> visibleKeys) {
        List<SidebarNodeDto> filtered = new ArrayList<>();
        for (SidebarNodeDto node : nodes) {
            if (node.getTabType() != null) {
                // Leaf item
                if (ADMIN_TAB_TYPES.contains(node.getTabType())) {
                    if (isAdmin) filtered.add(node);
                } else if (visibleKeys == null || visibleKeys.contains(node.getTabType())) {
                    // visibleKeys == null means admin (all visible)
                    filtered.add(node);
                }
            } else if (node.getChildren() != null) {
                // Folder — filter children, skip if empty
                List<SidebarNodeDto> children = filterNodes(node.getChildren(), isAdmin, visibleKeys);
                if (!children.isEmpty()) {
                    filtered.add(new SidebarNodeDto(node.getId(), node.getLabel(), null, children));
                }
            }
        }
        return filtered;
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
