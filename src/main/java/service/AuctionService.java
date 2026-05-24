package service;

import exception.ValidationException;
import model.auction.Auction;
import model.user.Bidder;
import model.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Service handling auction-related logic.
 */
public class AuctionService {

    /**
     * Validates auction creation parameters.
     */
    public void validateAuctionCreation(String type, String name, String priceStr, String extraInfo,
                                      LocalDate endDate, String endHourStr, String endMinStr,
                                      int startDelaySeconds) throws ValidationException {
        if (type == null) {
            throw new ValidationException("type", "Vui lòng chọn loại sản phẩm!");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("name", "Vui lòng nhập tên sản phẩm!");
        }
        if (priceStr == null || priceStr.trim().isEmpty()) {
            throw new ValidationException("price", "Vui lòng nhập giá khởi điểm!");
        }
        if (extraInfo == null || extraInfo.trim().isEmpty()) {
            throw new ValidationException("extraInfo", "Vui lòng nhập thông tin chi tiết!");
        }
        if (endDate == null || endHourStr == null || endMinStr == null) {
            throw new ValidationException("time", "Vui lòng chọn đầy đủ ngày và giờ kết thúc!");
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            throw new ValidationException("price", "Giá khởi điểm phải là một con số hợp lệ!");
        }
        if (startingPrice <= 0) {
            throw new ValidationException("price", "Giá khởi điểm phải lớn hơn 0!");
        }

        if (startDelaySeconds < 0) {
            throw new ValidationException("delay", "Thời gian bắt đầu phải là số nguyên dương!");
        }

        int endHour = Integer.parseInt(endHourStr);
        int endMin = Integer.parseInt(endMinStr);
        LocalDateTime endTime = LocalDateTime.of(endDate, LocalTime.of(endHour, endMin));
        LocalDateTime startTime = LocalDateTime.now().plusSeconds(startDelaySeconds);

        if (!endTime.isAfter(startTime)) {
            throw new ValidationException("time", "Thời điểm kết thúc phải sau thời điểm bắt đầu!");
        }
    }

    /**
     * Validates auction edit parameters.
     */
    public void validateAuctionEdit(String name, String priceStr, String extra, 
                                  LocalDate endDate, LocalDateTime startTime) throws ValidationException {
        if (name == null || name.trim().isEmpty() || priceStr == null || priceStr.trim().isEmpty() 
            || extra == null || extra.trim().isEmpty()) {
            throw new ValidationException("general", "Vui lòng điền đầy đủ các thông tin bắt buộc!");
        }
        if (endDate == null) {
            throw new ValidationException("endDate", "Vui lòng chọn ngày kết thúc!");
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(priceStr);
        } catch (NumberFormatException ex) {
            throw new ValidationException("price", "Giá khởi điểm phải là một con số!");
        }
        if (startingPrice <= 0) {
            throw new ValidationException("price", "Giá khởi điểm phải lớn hơn 0!");
        }

        LocalDateTime newEndTime = endDate.atTime(23, 59, 59);
        if (!newEndTime.isAfter(startTime)) {
            throw new ValidationException("time", "Thời điểm kết thúc phải sau thời điểm bắt đầu!");
        }
    }

    /**
     * Validates a bid before sending it to the server.
     *
     * @param auction     The auction to bid on.
     * @param amount      The bid amount.
     * @param currentUser The user making the bid.
     * @throws ValidationException if the bid is invalid.
     */
    public void validateBid(Auction auction, double amount, User currentUser) throws ValidationException {
        if (auction == null) {
            throw new ValidationException("auction", "Phiên đấu giá không tồn tại!");
        }

        if (amount <= 0) {
            throw new ValidationException("amount", "Số tiền phải lớn hơn 0!");
        }

        if (amount <= auction.getCurrentPrice()) {
            throw new ValidationException("amount", String.format("Giá đặt phải cao hơn giá hiện tại (%,.0f ₫)!",
                    auction.getCurrentPrice()));
        }

        if (!(currentUser instanceof Bidder)) {
            throw new ValidationException("user", "Chỉ người mua (Bidder) mới có quyền đặt giá!");
        }

        if (currentUser.getUserId().equals(auction.getSellerId())) {
            throw new ValidationException("user", "Bạn không thể đặt giá cho sản phẩm của chính mình!");
        }
        
        // KIỂM TRA SỐ DƯ TẠI CLIENT
        double currentBidOfUser = 0;
        if (auction.getHighestBid() != null && 
            auction.getHighestBid().getBidder().getUserId().equals(currentUser.getUserId())) {
            currentBidOfUser = auction.getHighestBid().getBidAmount();
        }
        
        double neededExtra = amount - currentBidOfUser;
        // Sử dụng một epsilon nhỏ (0.01) để an toàn với kiểu DOUBLE
        if (currentUser.getBalance() < (neededExtra - 0.01)) {
             throw new ValidationException("balance", String.format(
                     "Số dư khả dụng không đủ (Cần thêm %,.0f ₫)!", 
                     neededExtra - currentUser.getBalance()));
        }
    }

    /**
     * Builds the BID command string.
     */
    public String buildBidCommand(String auctionId, double amount, String userId) {
        return buildBidCommand(auctionId, amount, userId, false);
    }

    public String buildBidCommand(String auctionId, double amount, String userId, boolean isAnonymous) {
        return String.format(java.util.Locale.US, "BID:%s:%.2f:%s:%b", auctionId, amount, userId, isAnonymous);
    }
}
