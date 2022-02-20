import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/*
 *This class contains some functions that are frequently appearing in this program
 */
public class UsefulFunctions {


    /*Receives file indexer hash map, returns list of file it contains as a string*/
    public static String getFilesList(ConcurrentHashMap<String, FileObj> flist) {
        final String[] list = {"FILELIST "};

        flist.forEach((key, value) -> {
            list[0] = list[0] + (key + ":");

        });

        list[0] = list[0].substring(0, (list[0].length() - 1));
        return list[0];

    }

    /*
     *Receives a string that contains a space, returns the word after space
     *example: receives "Hello World", returns "World"
     */
    public static String get_path(String cmd) {

        String[] req = cmd.split(" ");
        return req[1];
    }

    /*
     *Receives a string that contains a space, returns the word before space
     *example: receives "Hello World", returns "Hello"
     */
    public static String get_request(String cmd) {

        String[] req = cmd.split(" ");
        return req[0];
    }

    /*
     *Receives a string that contains two spaces, returns the word after the second space
     *example: receives "I Love Java", returns "Java"
     */
    public static String getClientName(String cmd) {


        String[] req = cmd.split(" ", 3);
        return req[2];
    }

    /*
     *Receives two dates as a string, returns "keep" if the first date is more up-to-date than the second,
     *otherwise, return "update".
     *Used for syncing file indexer.
     */
    public static String compareDateTime(String old, String current) throws ParseException {
        String[] datetime1 = old.split("T");
        String[] datetime2 = current.split("T");
        datetime1[1] = datetime1[1].replace("Z", "");
        datetime2[1] = datetime2[1].replace("Z", "");

        old = datetime1[0] + " " + datetime1[1];
        current = datetime2[0] + " " + datetime2[1];

        Date newDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(old);
        Date oldDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(current);

        if (newDate.compareTo(oldDate) > 0) {
            //we are keeping the value
            return "keep";
        } else
            //we are updating the value
            return "update";


    }

    /*
     *Returns a random 10-character string
     *Uses:
     * 1. Giving a file a temporary name, instead of saving it straight with its original name
     * 2. Giving a client a random name, so that all servers could recognize it by a single name, instead by remote socket address
     */
    public static String GeneratingRandomAlphabetic() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return generatedString;
    }

    /*Returns the date and time of NOW as a string*/
    public static String getDateTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ISO_INSTANT;
        ZonedDateTime now = ZonedDateTime.now().plusHours(2);

        return dtf.format(now.truncatedTo(ChronoUnit.SECONDS));

    }


    /*
     *Receives a file name and DataInputStream
     *Creates a file in the storage path with the given name, and receives its content with the given DataInputStream
     */
    public static void receiveFile(String fileName, DataInputStream dis) {
        int bytes = 0;
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(ServerMulti.storagePath + fileName);
            long size = dis.readLong();     // read file size
            byte[] buffer = new byte[4 * 1024];
            while (size > 0 && (bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                fileOutputStream.write(buffer, 0, bytes);
                size -= bytes;      // read upto file size
            }
            fileOutputStream.close();
        } catch (IOException e) {
            ServerMulti.logger.info("Couldn't receive file." + e.getMessage());

            e.printStackTrace();
        }


    }


    /*
     *Receives a file name and DataOutputStream
     *Finds given file name in the storage path, and sends its content with the given DataOutputStream
     */
    public static void sendFile(String filename, DataOutputStream dos) {
        int bytes = 0;
        File file = new File(ServerMulti.storagePath + filename);
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            // send file size
            dos.writeLong(file.length());
            // break file into chunks
            byte[] buffer = new byte[4 * 1024];
            while ((bytes = fileInputStream.read(buffer)) != -1) {
                dos.write(buffer, 0, bytes);
                dos.flush();
            }
            fileInputStream.close();
        } catch (IOException e) {
            ServerMulti.logger.info("Couldn't send file" + e.getMessage());
            e.printStackTrace();
        }


    }

    /*
     *Receives a file name
     *with the help of getFileChecksum() function, it returns the sha-256 digest string of the given file
     */
    public static String sha256Fixed(String filename) {
        MessageDigest shaDigest = null;
        try {
            shaDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            ServerMulti.logger.warning("Couldn't do digest calculation" + e.getMessage());


            e.printStackTrace();
        }
        String shaChecksum = getFileChecksum(shaDigest, new File(ServerMulti.storagePath + filename));
        return shaChecksum;
    }

    /*
     *Function that helps to calculate the sha-256 digest of a files content
     */
    private static String getFileChecksum(MessageDigest digest, File file) {
        //Get file input stream for reading the file content
        //from here
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            //Create byte array to read data in chunks
            byte[] byteArray = new byte[1024];
            int bytesCount = 0;

            //Read file data and update in message digest
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            ;

            //close the stream; We don't need it now.
            fis.close();
        } catch (IOException e) {
            ServerMulti.logger.warning("Couldn't do digest calculation" + e.getMessage());

            e.printStackTrace();
        }

        //Get the hash's bytes
        byte[] bytes = digest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString();
    }
}
