package thebrightcompany.com.boxexpress.model;

public class BoxExpress {

    private String TransactionID;
    private String BoxInfo;
    private String Sdt;

    public BoxExpress(String transactionID, String boxInfo, String sdt) {
        TransactionID = transactionID;
        BoxInfo = boxInfo;
        Sdt = sdt;
    }

    public String getTransactionID() {
        return TransactionID;
    }

    public void setTransactionID(String transactionID) {
        TransactionID = transactionID;
    }

    public String getBoxInfo() {
        return BoxInfo;
    }

    public void setBoxInfo(String boxInfo) {
        BoxInfo = boxInfo;
    }

    public String getSdt() {
        return Sdt;
    }

    public void setSdt(String sdt) {
        Sdt = sdt;
    }
}
