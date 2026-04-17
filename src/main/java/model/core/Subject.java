package model.core;

public interface Subject {
    void attach(Observer observer); // Thêm người theo dõi
    void detach(Observer observer); // Xóa người theo dõi
    void notifyObservers(String message); // Gửi thông báo cho tất cả
}