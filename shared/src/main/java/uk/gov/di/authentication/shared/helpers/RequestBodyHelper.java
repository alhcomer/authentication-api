package uk.gov.di.authentication.shared.helpers;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class RequestBodyHelper {

    private static final Logger LOG = LogManager.getLogger(RequestBodyHelper.class);

    public static Map<String, String> parseRequestBody(String body) {
        LOG.info("Parsing request body");
        Map<String, String> query_pairs = new HashMap<>();

        for (NameValuePair pair : URLEncodedUtils.parse(body, Charset.defaultCharset())) {
            query_pairs.put(pair.getName(), pair.getValue());
        }

        return query_pairs;
    }
}
