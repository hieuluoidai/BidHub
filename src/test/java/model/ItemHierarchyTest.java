package model;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.Vehicle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho cây kế thừa Item – kiểm tra tính đa hình và kế thừa
 * giữa Electronics, Art, Vehicle.
 */
@DisplayName("Item hierarchy – kiểm tra kế thừa và đa hình")
class ItemHierarchyTest {

    @Test
    @DisplayName("Electronics kế thừa từ Item")
    void electronics_isItem() {
        Item i = new Electronics("I_01", "Laptop", "desc", 500.0, "Dell");
        assertInstanceOf(Item.class, i);
    }

    @Test
    @DisplayName("Art kế thừa từ Item")
    void art_isItem() {
        Item i = new Art("I_02", "Tranh", "desc", 1000.0, "Van Gogh");
        assertInstanceOf(Item.class, i);
    }

    @Test
    @DisplayName("Vehicle kế thừa từ Item")
    void vehicle_isItem() {
        Item i = new Vehicle("I_03", "Xe máy", "desc", 30000.0, "Honda");
        assertInstanceOf(Item.class, i);
    }

    @Test
    @DisplayName("Đa hình: getItemType trả về đúng loại cho mỗi class")
    void polymorphism_getItemType_worksCorrectly() {
        Item[] items = {
            new Electronics("I_01", "Laptop", "desc", 500.0,   "Dell"),
            new Art        ("I_02", "Tranh",  "desc", 1000.0,  "Monet"),
            new Vehicle    ("I_03", "Xe",     "desc", 30000.0, "Toyota")
        };

        assertEquals("Electronics", items[0].getItemType());
        assertEquals("Art",         items[1].getItemType());
        assertEquals("Vehicle",     items[2].getItemType());
    }

    @Test
    @DisplayName("Electronics lưu đúng brand")
    void electronics_storesBrand() {
        Electronics e = new Electronics("I_01", "iPhone", "desc", 1000.0, "Apple");
        assertEquals("Apple", e.getBrand());
    }

    @Test
    @DisplayName("Art lưu đúng artist")
    void art_storesArtist() {
        Art a = new Art("I_02", "Tranh Mona Lisa", "desc", 5000.0, "Da Vinci");
        assertEquals("Da Vinci", a.getArtist());
    }

    @Test
    @DisplayName("Vehicle lưu đúng brand")
    void vehicle_storesBrand() {
        Vehicle v = new Vehicle("I_03", "Civic", "desc", 25000.0, "Honda");
        assertEquals("Honda", v.getBrand());
    }

    @Test
    @DisplayName("Các getter chung kế thừa từ Item hoạt động đúng")
    void commonGetters_workCorrectly() {
        Electronics item = new Electronics(
            "I_01", "MacBook", "Máy tính xách tay", 2000.0, "Apple"
        );

        assertEquals("I_01",                item.getItemId());
        assertEquals("MacBook",             item.getItemName());
        assertEquals("Máy tính xách tay",   item.getDescription());
        assertEquals(2000.0,                item.getStartingPrice(), 0.001);
    }
}