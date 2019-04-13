package com.docview.dto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MimePart {
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
	TXT("text/plain"){
		@Override
		public String toString() {
			return "text";
			
		}
	},
	UNK("application/octet-stream"){
		@Override
		public String toString() {
			return "unknown";
			
		}
	};
	private MimePart(String mime) {
		this.mime = mime;
	}
	private final String mime;
	public String mimeType() {
		return mime;
	}
	/**
	 * 
	 * @param mime
	 * @return
	 */
	public static MimePart ofType(String mime) {
		if(mime != null && MIMES.containsKey(mime))
			return MIMES.get(mime);
		else
			return UNK;
	}
	/**
	 * 
	 * @param file
	 * @return
	 */
	public static MimePart ofType(Path file) {
		String mime = null;
		try {
			mime = Files.probeContentType(file);
		} catch (Exception e) {}
		
		return ofType(mime);
	}
	private static final Map<String, MimePart> MIMES = EnumSet.allOf(MimePart.class).stream().collect(Collectors.toMap(MimePart::mimeType, Function.identity()));
}
