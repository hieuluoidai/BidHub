package utils;

import java.io.IOException;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneSwitcher {
	public static void changeScene(ActionEvent event, String fxmlPath) throws IOException {
	    java.net.URL resource = SceneSwitcher.class.getResource(fxmlPath);
	    if (resource == null) {
	        System.out.println("Fatal Error: Could not find FXML file at: " + fxmlPath);
	        throw new IOException("FXML file not found at " + fxmlPath);
	    }
	    
	    Parent root = FXMLLoader.load(resource);
	    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
	    stage.setScene(new Scene(root));
	    stage.show();
	}
}
