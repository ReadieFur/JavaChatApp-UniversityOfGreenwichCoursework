package chat_app.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

//This attribute (annotation) is based on my C# "Command" attribute: https://github.com/ReadieFur/CreateProcessAsUser/blob/fd80746a175c52bd64edc40a5c1e590c65c171d5/src/CreateProcessAsUser.Service/UserInteractive.cs#L422-L434
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandAttribute
{
    String description();
    int availableInMode(); //0 = both, 1 = server, 2 = client
}
