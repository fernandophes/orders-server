package br.edu.ufersa.cc.sd.utils;

import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public interface JsonUtils {

    final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    final ObjectReader READER = MAPPER.reader();
    final ObjectWriter WRITER = MAPPER.writer().withDefaultPrettyPrinter();

    public static String toJson(final Object object) {
        try {
            return WRITER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static <T> T fromJson(final String json) {
        try {
            return READER.readValue(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

}
