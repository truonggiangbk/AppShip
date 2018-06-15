package thebrightcompany.com.boxexpress.model;

public class BoxExpress {

    private String location;
    private int message_id;
    private String transaction_id;

    public BoxExpress(String location, int message_id, String transaction_id) {
        this.location = location;
        this.message_id = message_id;
        this.transaction_id = transaction_id;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getMessage_id() {
        return message_id;
    }

    public void setMessage_id(int message_id) {
        this.message_id = message_id;
    }

    public String getTransaction_id() {
        return transaction_id;
    }

    public void setTransaction_id(String transaction_id) {
        this.transaction_id = transaction_id;
    }
}
