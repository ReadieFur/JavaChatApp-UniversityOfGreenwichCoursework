package chat_app.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CommandParameterAttributes
{
    CommandParameterAttribute[] value();
}
