package com.abbup.extension.model;

public class Connection {
	private String userId;
	private String webhookUrl;
	
	public Connection(String userId, String webhookUrl) {
		super();
		this.userId = userId;
		this.webhookUrl = webhookUrl;
	}

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }

	public String getWebhookUrl() { return webhookUrl; }
	public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
	
}
