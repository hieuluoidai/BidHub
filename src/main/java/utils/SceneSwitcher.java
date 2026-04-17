package utils;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class SceneSwitcher {

	public static void switchScene(Event event, String fxmlFilePath) {
		try {
			FXMLLoader loader = new FXMLLoader(SceneSwitcher.class.getResource(fxmlFilePath));
			Parent root = loader.load();

			// Lấy cửa sổ hiện tại dựa trên sự kiện (bấm nút hay click chuột đều được)
			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

			Scene scene = new Scene(root);
			stage.setScene(scene);
			stage.show();

		} catch (IOException e) {
			System.out.println("Lỗi không tìm thấy file FXML: " + fxmlFilePath);
			e.printStackTrace();
		}
	}
}