<center>
<a href="https://vaadin.com">
 <img src="https://vaadin.com/images/hero-reindeer.svg" width="200" height="200" /></a>
</center>

# Vaadin V10 app with I18N Page Title, dynamically created
What I want to show in this example is, how you could deal with 
a dynamic page title per view (or time or whatever)
that will handle your browser Locale as well.

## the Implementation and Usage
The solution should :

* be based on message bundles
* not be inside inheritance
* based on Annotations
* be easy to extend
* be able to change the language during runtime

### The developer / user view
Mostly it is an excellent approach to develop a solution for a developer 
from the perspective of a developer.
Here it means, what should a developer see if he/she have to use your solution.

The developer will see this Annotation.
Three things can be defined here. 

* The message key that will be used to resolve the message based on the actual Locale
* A default value the will be used, if no corresponding resource key was found neither fallback language is provided 
* Definition of the message formatter, default Formatter will only return the translated key.


```java
@Retention(RetentionPolicy.RUNTIME)
public @interface I18NPageTitle {
  String messageKey() default "";
  String defaultValue() default "";
  Class< ? extends TitleFormatter> formatter() default DefaultTitleFormatter.class;
}
```

The default usage should look like the following one.

```java
@Route(View003.VIEW_003)
@I18NPageTitle(messageKey = "view.title")
public class View003 extends Composite<Div> implements HasLogger {
  public static final String VIEW_003 = "view003";
}
```

Now we need a way to resolve the final message and the right point in time to set the title.
Here we could use the following interfaces.

* VaadinServiceInitListener, 
* UIInitListener, 
* BeforeEnterListener

With these interfaces we can hook into the life cycle of a view. At this time slots, we have all the information's we need. 
The Annotation to get the message key and the locale of the current request.

The class that is implementing all these interfaces is called **I18NPageTitleEngine**

```java
public class I18NPageTitleEngine 
       implements VaadinServiceInitListener, 
                  UIInitListener, 
                  BeforeEnterListener, 
                  HasLogger {

  public static final String ERROR_MSG_NO_LOCALE = "no locale provided and i18nProvider #getProvidedLocales()# list is empty !! ";
  public static final String ERROR_MSG_NO_ANNOTATION = "no annotation found at class ";

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    Class<?> navigationTarget = event.getNavigationTarget();
    I18NPageTitle annotation = navigationTarget.getAnnotation(I18NPageTitle.class);
    match(
        matchCase(() -> success(annotation.messageKey())) ,
        matchCase(() -> annotation == null ,
                  () -> failure(ERROR_MSG_NO_ANNOTATION + navigationTarget.getName())) ,
        matchCase(() -> annotation.messageKey().isEmpty() ,
                  () -> success(annotation.defaultValue()))
    )
        .ifPresentOrElse(
            msgKey -> {
              final I18NProvider i18NProvider = VaadinService
                  .getCurrent()
                  .getInstantiator()
                  .getI18NProvider();
              final Locale locale = event.getUI().getLocale();
              final List<Locale> providedLocales = i18NProvider.getProvidedLocales();
              match(
                  matchCase(() -> success(providedLocales.get(0))) ,
                  matchCase(() -> locale == null && providedLocales.isEmpty() ,
                            () -> failure(ERROR_MSG_NO_LOCALE + i18NProvider.getClass().getName())) ,
                  matchCase(() -> locale == null ,
                            () -> success(providedLocales.get(0))) ,
                  matchCase(() -> providedLocales.contains(locale) ,
                            () -> success(locale))
              ).ifPresentOrElse(
                  finalLocale -> ((CheckedFunction<Class<? extends TitleFormatter>, TitleFormatter>) f -> f.getDeclaredConstructor().newInstance())
                      .apply(annotation.formatter())
                      .ifPresentOrElse(
                          formatter -> formatter
                              .apply(i18NProvider , finalLocale , msgKey).
                                  ifPresentOrElse(title -> UI.getCurrent()
                                                             .getPage()
                                                             .setTitle(title) ,
                                                  failed -> logger().info(failed)) ,
                          failed -> logger().info(failed)) ,
                  failed -> logger().info(failed));
            }
            , failed -> logger().info(failed));
  }

  @Override
  public void uiInit(UIInitEvent event) {
    final UI ui = event.getUI();
    ui.addBeforeEnterListener(this);
    //addListener(ui, PermissionsChangedEvent.class, e -> ui.getPage().reload());
  }

  @Override
  public void serviceInit(ServiceInitEvent event) {
    event
        .getSource()
        .addUIInitListener(this);
  }
}
```
The method with the name **beforeEnter** is the critical part. Here you can see how the key is resolved.
However, there is one new thing.  Let´s have a look at the following lines.

```java
              final I18NProvider i18NProvider = VaadinService
                  .getCurrent()
                  .getInstantiator()
                  .getOrCreate(VaadinI18NProvider.class);
```

This few lines are introducing a new thing, that is available in Vaadin 10.
The interface **I18NProvider** is used to implement a mechanism for the internationalisation 
of Vaadin applications.

The interface is simple and with only two methods to implement.

```java
public interface I18NProvider extends Serializable {
    List<Locale> getProvidedLocales();
    String getTranslation(String key, Locale locale, Object... params);
}
```

The first one should give back the list of Locales that could be handled from this implementation.
The second method is used to translate the message key. 
In this method, the handling of a default translation or better the switch into a default language should be handled. Missing keys can be handled differently. Some developers are throwing an exception, but I prefer to return the key itself, 
together with the locale from the original request. 
This information is mostly better to use as a stack trace.

The solution that is bundled with this demo can handle the Locales EN ad DE, the fallback will be the locale EN.
The implementation is not dealing with reloads of message bundles during runtime or other features that are needed for professional environments.

```java
public class VaadinI18NProvider implements I18NProvider, HasLogger {

  public VaadinI18NProvider() {
    logger().info("VaadinI18NProvider was found..");
  }

  public static final String RESOURCE_BUNDLE_NAME = "vaadinapp";

  private static final ResourceBundle RESOURCE_BUNDLE_EN = getBundle(RESOURCE_BUNDLE_NAME , ENGLISH);
  private static final ResourceBundle RESOURCE_BUNDLE_DE = getBundle(RESOURCE_BUNDLE_NAME , GERMAN);


  @Override
  public List<Locale> getProvidedLocales() {
    logger().info("VaadinI18NProvider getProvidedLocales..");
    return List.of(ENGLISH ,
                   GERMAN);
  }

  @Override
  public String getTranslation(String key , Locale locale , Object... params) {
//    logger().info("VaadinI18NProvider getTranslation.. key : " + key + " - " + locale);
    return match(
        matchCase(() -> success(RESOURCE_BUNDLE_EN)) ,
        matchCase(() -> GERMAN.equals(locale) , () -> success(RESOURCE_BUNDLE_DE)) ,
        matchCase(() -> ENGLISH.equals(locale) , () -> success(RESOURCE_BUNDLE_EN))
    )
        .map(resourceBundle -> {
          if (! resourceBundle.containsKey(key))
            logger().info("missing ressource key (i18n) " + key);

          return (resourceBundle.containsKey(key)) ? resourceBundle.getString(key) : key;

        })
        .getOrElse(() -> key + " - " + locale);
  }
}
```
The Interface **I18NProvider** is implemented for example by the abstract class **Component**.
Having this in mind, we are now using the same mechanism for the page title as well as inside a Component. 

The last thing you should not forget is the activation of the **I18NProvider** implementation itself.
There are several ways you can use; I am using a simple approach inside the primary method that will start my app itself.

```setProperty("vaadin.i18n.provider", VaadinI18NProvider.class.getName());```



```java
public class BasicTestUIRunner {
  private BasicTestUIRunner() {
  }

  public static void main(String[] args) {
    setProperty("vaadin.i18n.provider", VaadinI18NProvider.class.getName());
    
    new Meecrowave(new Meecrowave.Builder() {
      {
//        randomHttpPort();
        setHttpPort(8080);
        setTomcatScanning(true);
        setTomcatAutoSetup(false);
        setHttp2(true);
      }
    })
        .bake()
        .await();
  }
}
```

The Vaadin documentation will give you more detailed information´s about this.

The last step for today is the activation of our **I18NPageTitleEngine**
This activation is done inside the file with the name **com.vaadin.flow.server.VaadinServiceInitListener**
you have to create inside the folder  **META-INF/services** 
The only line we have to add is the fully qualified name of our class.

```
org.rapidpm.vaadin.api.i18.I18NPageTitleEngine
```

If you have questions or something to discuss..  ping me via
email [mailto::sven.ruppert@gmail.com](sven.ruppert@gmail.com)
or via Twitter : [https://twitter.com/SvenRuppert](@SvenRuppert)





