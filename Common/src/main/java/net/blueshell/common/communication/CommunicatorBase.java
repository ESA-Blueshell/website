package net.blueshell.common.communication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

public class CommunicatorBase implements ICommunicator {
    private static final Logger logger = Logger.getLogger(CommunicatorBase.class.getName());

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type, String body, HashMap<String, Object> parameters) {
        try {
            // URL of the localhost endpoint
            URI uri = new URI(url);
            URL uriURL = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) uriURL.openConnection();

            // Set the request method to GET
            connection.setRequestMethod("GET");

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print the response
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);

        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
        return new ResponseEntity<>("Could not send request!", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type, String body, HashMap<String, Object> parameters) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
