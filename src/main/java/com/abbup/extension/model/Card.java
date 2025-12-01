package com.abbup.extension.model;

public class Card {
	private String title;
	private String thumbnail;
	private String theme;
	
	public Card(String title, String thumbnail, String theme) {
		super();
		this.title = title;
		this.thumbnail = thumbnail;
		this.theme = theme;
	}

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getThumbnail() { return thumbnail; }
	public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

	public String getTheme() { return theme; }
	public void setTheme(String theme) { this.theme = theme; }
	
}
