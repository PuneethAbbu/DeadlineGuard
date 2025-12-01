package com.abbup.extension.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.abbup.extension.model.Button;
import com.abbup.extension.model.Card;

@Component
public class MessageComponents {
	public String createText(String text) {
		return text;
	}
	
	public Map<String, String> createCard(Card card) {
		return Map.ofEntries(
					Map.entry("title", card.getTitle()),
					Map.entry("thumbnail", card.getThumbnail()),
					Map.entry("theme", card.getTheme())
				);
	}
	
	public List<Object> createButtons(Button... buttons) {
		List<Object> buttonObjects = new ArrayList<>();
		for(Button button: buttons) buttonObjects.add(this.createUrlButton(button));
		return buttonObjects;
	}
	
	private Map<String, Object> createUrlButton(Button button) {
		String label = button.getLabel();
		String actionType = button.getActionType();
		String actionUrl = button.getActionUrl();
		String type = button.getType();
		
		Map<String, Object> dataMap = new HashMap<>();
		
		if ("open.url".equals(button.getActionType())) dataMap.put("web", actionUrl); 
		else dataMap.put("name", actionUrl);
    

        Map<String, Object> actionMap = Map.ofEntries(
        									Map.entry("type", actionType),
        									Map.entry("data", dataMap)
        								);

        Map<String, Object> buttonMap = Map.ofEntries(
        									Map.entry("label", label),
        									Map.entry("type", type),
        									Map.entry("action", actionMap)
        								);
        System.out.println(buttonMap);
        return buttonMap;
	}
	
	public List<Object> createTable(List<String> headers, List<Map<String, String>> rows, String title) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("headers", headers);
        dataMap.put("rows", rows);

        Map<String, Object> slideMap = new HashMap<>();
        slideMap.put("type", "table");
        slideMap.put("title", title);
        slideMap.put("data", dataMap);

        List<Object> slides = new ArrayList<>();
        slides.add(slideMap);
        
        return slides;
    }
}
