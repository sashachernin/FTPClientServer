import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class FileObj {
    String hash;
    String datetime;
    private String lockedby="none";

    FileObj(String hash, String datetime)
    {
        DateTimeFormatter dtf = DateTimeFormatter.ISO_INSTANT;
        ZonedDateTime now = ZonedDateTime.now().plusHours(2);


        this.hash=hash;
        this.datetime=datetime;
    }

    public void setLockedby(String lockedby) {
        this.lockedby = lockedby;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public String getLockedby() {
        return lockedby;
    }
}