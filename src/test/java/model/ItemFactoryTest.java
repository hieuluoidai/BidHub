package model;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.ItemFactory;
import model.item.Vehicle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho ItemFactory – kiểm tra Factory Pattern có tạo đúng loại sản phẩm.
 */
@DisplayName("ItemFactory – kiểm tra Factory Pattern")
class ItemFactoryTest {

    private static final String ID    = "ITEM_001";
    private static final String NAME  = "Sản phẩm test";
    private static final String DESC  = "Mô tả test";
    private static final double PRICE = 500.0;

    @Test
    @DisplayName("Tạo Electronics từ type 'ELECTRONICS'")
    void createItem_electronicsType_returnsElectronics() {
        Item item = ItemFactory.createItem("ELECTRONICS", ID, NAME, DESC, PRICE, "Apple");
        assertNotNull(item);
        assertInstanceOf(Electronics.class, item);
        assertEquals("Electronics", item.getItemType());
    }

    @Test
    @DisplayName("Tạo Art từ type 'ART'")
    void createItem_artType_returnsArt() {
        Item item = ItemFactory.createItem("ART", ID, NAME, DESC, PRICE, "Van Gogh");
        assertNotNull(item);
        assertInstanceOf(Art.class, item);
        assertEquals("Art", item.getItemType());
    }

    @Test
    @DisplayName("Tạo Vehicle từ type 'VEHICLE'")
    void createItem_vehicleType_returnsVehicle() {
        Item item = ItemFactory.createItem("VEHICLE", ID, NAME, DESC, PRICE, "Toyota");
        assertNotNull(item);
        assertInstanceOf(Vehicle.class, item);
        assertEquals("Vehicle", item.getItemType());
    }

    @Test
    @DisplayName("Type chữ thường 'electronics' vẫn tạo được Electronics")
    void createItem_lowercaseType_stillWorks() {
        Item item = ItemFactory.createItem("electronics", ID, NAME, DESC, PRICE, "Samsung");
        assertInstanceOf(Electronics.class, item);
    }

    @Test
    @DisplayName("Type chữ hoa-thường lẫn lộn 'Art' vẫn tạo được Art")
    void createItem_mixedCaseType_stillWorks() {
        Item item = ItemFactory.createItem("Art", ID, NAME, DESC, PRICE, "Monet");
        assertInstanceOf(Art.class, item);
    }

    @Test
    @DisplayName("Thông tin sản phẩm được truyền đúng vào constructor")
    void createItem_passesDataCorrectly() {
        Item item = ItemFactory.createItem("ELECTRONICS", ID, NAME, DESC, PRICE, "Dell");
        assertEquals(ID,    item.getItemId());
        assertEquals(NAME,  item.getItemName());
        assertEquals(DESC,  item.getDescription());
        assertEquals(PRICE, item.getStartingPrice(), 0.001);
    }

    @Test
    @DisplayName("Thông tin brand được set đúng cho Electronics")
    void createItem_electronicsBrand_setCorrectly() {
        Electronics item = (Electronics) ItemFactory.createItem(
            "ELECTRONICS", ID, NAME, DESC, PRICE, "Apple"
        );
        assertEquals("Apple", item.getBrand());
    }

    @Test
    @DisplayName("Thông tin artist được set đúng cho Art")
    void createItem_artArtist_setCorrectly() {
        Art item = (Art) ItemFactory.createItem(
            "ART", ID, NAME, DESC, PRICE, "Picasso"
        );
        assertEquals("Picasso", item.getArtist());
    }

    @Test
    @DisplayName("Thông tin brand được set đúng cho Vehicle")
    void createItem_vehicleBrand_setCorrectly() {
        Vehicle item = (Vehicle) ItemFactory.createItem(
            "VEHICLE", ID, NAME, DESC, PRICE, "Honda"
        );
        assertEquals("Honda", item.getBrand());
    }

    @Test
    @DisplayName("Type không hợp lệ phải ném IllegalArgumentException")
    void createItem_invalidType_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ItemFactory.createItem("KITCHEN", ID, NAME, DESC, PRICE, "info")
        );
        assertTrue(ex.getMessage().contains("Loại sản phẩm không hợp lệ"));
    }

    @Test
    @DisplayName("Type rỗng phải ném IllegalArgumentException")
    void createItem_emptyType_throwsException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ItemFactory.createItem("", ID, NAME, DESC, PRICE, "info")
        );
    }
}