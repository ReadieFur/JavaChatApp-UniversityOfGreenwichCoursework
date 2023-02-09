package chat_app.attributes;

import java.lang.annotation.Repeatable;

@Repeatable(CommandParameterAttributes.class)
public @interface CommandParameterAttribute
{
    String name();
    String description();
}
