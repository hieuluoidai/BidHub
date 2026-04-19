package model.core;

/**
 * Interface quản lý (hủy)đăng ký và phát tin cho người theo dõi.
 */
public interface Subject {
    // Đăng ký nhận thông báo
    void attach(Observer observer);

    // Hủy đăng ký nhận thông báo
    void detach(Observer observer);

    // Phát tín hiệu tới toàn bộ danh sách đã đăng ký
    void notifyObservers(String message);
}