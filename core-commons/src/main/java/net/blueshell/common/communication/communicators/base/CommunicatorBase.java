package net.blueshell.common.communication.communicators.base;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.blueshell.common.Constants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

public abstract class CommunicatorBase implements ICommunicator {

    private static final Logger logger = Logger.getLogger(CommunicatorBase.class.getName());
    private final RabbitTemplate template;

    public CommunicatorBase() {
        this.template = null;
    }

    public CommunicatorBase(RabbitTemplate template) {
        this.template = template;
    }

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        try {
            // URL of the localhost endpoint
            URI uri = new URI(url);
            URL uriURL = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) uriURL.openConnection();

            connection.setRequestMethod(type.toString());
            connection.setDoOutput(true);

            if(body != null)
            {
                // Write the body to the output stream
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            // Get the response code
            int responseCode = connection.getResponseCode();
            logger.log(Level.INFO, "Response Code: " + responseCode);

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
    public String sendAsync(String targetName, String body) {

        try {
            assert this.template != null;

// TODO add routing
//            this.template.convertAndSend(
//                    Constants.QUEUE_EXCHANGE_NAME,
//                    Constants.QUEUE_ROUTE_PREFIX + "." + targetName,
//                    body
//            );

            this.template.convertAndSend(Constants.EXCHANGE, Constants.QUEUE_ROUTE_PREFIX + "." + targetName, body);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "Could not send message to " + targetName;
        }

        return "Message sent to " + targetName;
    }

    public abstract String getName();
}
