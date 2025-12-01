package com.abbup.extension.model;

public class Button {
	private String label;
	private String actionType;
	private String actionUrl;
	private String type;
	
	public Button(String label, String actionType, String actionUrl, String type) {
		super();
		this.label = label;
		this.actionType = actionType;
		this.actionUrl = actionUrl;
		this.type = type;
	}

	public String getLabel() { return label; }
	public void setLabel(String label) { this.label = label; }

	public String getActionType() { return actionType; }
	public void setActionType(String actionType) { this.actionType = actionType; }

	public String getActionUrl() { return actionUrl; }
	public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

	public String getType() { return type; }
	public void setType(String type) { this.type = type; }

}
