package com.docview.dto;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Mime {
	PDF("application/pdf"){
		@Override
		public String toString() {
			return "pdf";
			
		}
	},
	DOC("application/msword"){
		@Override
		public String toString() {
			return "ms document";
			
		}
	},
	GDOC("application/vnd.google-apps.document"){
		@Override
		public String toString() {
			return "google document";
			
		}
	},
	ODOC("application/vnd.openxmlformats-officedocument.wordprocessingml.document"){
		@Override
		public String toString() {
			return "open document";
			
		}
	},
	JPG("image/jpg"){
		@Override
		public String toString() {
			return "jpg image";
			
		}
	},
	PNG("image/png"){
		@Override
		public String toString() {
			return "png image";
			
		}
	},
	VID("video/mp4"){
		@Override
		public String toString() {
			return "mp4 video";
			
		}
	},
	GDIR("application/vnd.google-apps.folder"){
		@Override
		public String toString() {
			return "google folder";
			
		}
	},
	UNK("application/octet-stream"){
		@Override
		public String toString() {
			return "unknown";
			
		}
	};
	private Mime(String mime) {
		this.mime = mime;
	}
	private final String mime;
	public String mimeType() {
		return mime;
	}
	public static Mime valueOfMime(String mime) {
		return MIMES.get(mime);
	}
	private static final Map<String, Mime> MIMES = EnumSet.allOf(Mime.class).stream().collect(Collectors.toMap(Mime::mimeType, Function.identity()));
}
