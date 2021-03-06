package l2server.gameserver.model.event.annotations;

import l2server.gameserver.model.event.ListenerRegisterType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RegisterType
{
    ListenerRegisterType value();
}