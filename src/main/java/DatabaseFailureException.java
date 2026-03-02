// taken from class repo
public class DatabaseFailureException extends Exception{

    public DatabaseFailureException(String message){
        super("Database Failed!" + message);
    }
}