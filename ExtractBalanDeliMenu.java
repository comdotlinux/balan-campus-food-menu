///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS org.jsoup:jsoup:1.15.4
//DEPS com.google.flogger:flogger:0.7.4
//DEPS com.google.flogger:flogger-system-backend:0.7.4

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.flogger.FluentLogger;
import org.jsoup.Jsoup;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "ExtractBalanDeliMenu", mixinStandardHelpOptions = true, version = "ExtractBalanDeliMenu 0.1", description = "ExtractBalanDeliMenu made with jbang")
class ExtractBalanDeliMenu implements Callable<Integer> {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private final String ocrApiKey;
    private final boolean mockOcrAPiCall;
    private final String deeplApiKey;
    private final boolean mockDeeplAPiCall;

    public ExtractBalanDeliMenu() {
        mockOcrAPiCall = "true".equalsIgnoreCase(System.getenv("MOCK_OCR"));
        ocrApiKey = mockOcrAPiCall ? "mock-api-key" : Objects.requireNonNull(System.getenv("OCR_API_KEY"), "Please provide a OCR api key as OCR_API_KEY from https://ocr.space/");
        mockDeeplAPiCall = "true".equalsIgnoreCase(System.getenv("MOCK_DEEPL"));
        deeplApiKey = mockDeeplAPiCall ? "mock-api-key" : Objects.requireNonNull(System.getenv("DEEPL_API_KEY"), "Please provide a Deepl api key as DEEPL_API_KEY from https://www.deepl.com/pro-api?cta=header-pro-api");
        logger.atInfo().log("Mocked Ocr Api Call? %b, Mocked Deepl Api Call? %b", mockOcrAPiCall, mockDeeplAPiCall);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String... args) {
        int exitCode = new CommandLine(new ExtractBalanDeliMenu()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        var imageUrl = menuImageUrl();
        logger.atInfo().log("ImageUrl: %s", imageUrl);
        var menuInGerman = doOcr(imageUrl, mockOcrAPiCall).orElseThrow();
        List<Menu> weeklyGermanMenu = extractMenu(menuInGerman);

        List<String> translationInput = weeklyGermanMenu.stream().map(menu -> List.of(menu.one(), menu.two())).flatMap(List::stream).toList();
        List<String> translatedText = translateToEnglish(translationInput);
        List<Menu> weeklyEnglishMenu = convertToWeeklyMenu(weeklyGermanMenu, translatedText);

        logger.atInfo().log("Menu in German  : %s", weeklyGermanMenu.isEmpty() ? menuInGerman : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(weeklyGermanMenu));
        logger.atInfo().log("Menu in English : %s", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(weeklyEnglishMenu));
        return 0;
    }

    private static List<Menu> convertToWeeklyMenu(List<Menu> weeklyGermanMenu, List<String> translatedText) {
        List<Menu> weeklyEnglishMenu = new ArrayList<>();

        // 0 -> 0,1 ; 1 -> 2,3 ; 2 -> 4,5 ; 3 -> 6,7, 4 -> 8,9
        for (int row = 0; row < weeklyGermanMenu.size(); row++) {
            var menuOneIndex = row == 0 ? row : (row * 2);
            var menuTwoIndex = menuOneIndex + 1;
            Menu menu = weeklyGermanMenu.get(row);
            weeklyEnglishMenu.add(new Menu(menu.day(), menu.date(), translatedText.get(menuOneIndex), translatedText.get(menuTwoIndex)));
        }
        return weeklyEnglishMenu;
    }


    private static final Pattern dateRegex = Pattern.compile("(\\d{2})(\\d{2})(\\d{4})");

    private List<Menu> extractMenu(String menuInGerman) {
        List<Menu> menus = new ArrayList<>();
        var menuLines = Arrays.stream(menuInGerman.split("\n")).map(s -> s.replaceAll("•", "").replaceAll("\\.", "").strip()).toArray(String[]::new);
        for (int lineNumber = 0; lineNumber < menuLines.length; lineNumber++) {
            var line = menuLines[lineNumber];
            Optional<Weekday> weekday = Weekday.find(wd -> line.startsWith(wd.inGerman));
            if (weekday.isPresent()) {
                var menuOne = menuLines[lineNumber + 1];
                var menuTwo = menuLines[lineNumber + 2];
                Matcher matcher = dateRegex.matcher(line);
                logger.atInfo().log("line %s matcher : %s", line, matcher);
                String date = line;
                if (matcher.find()) {
                    date = String.format("%s.%s.%s", matcher.group(1), matcher.group(2), matcher.group(3));
                }
                Menu menu = new Menu(weekday.get(), date, menuOne, menuTwo);
                menus.add(menu);
                logger.atFine().log("Menu %s %n", menu);
            }
        }
        return menus;
    }

    private static final String balanDeliMenuUrl = "https://www.balan-deli.com/speisen";

    private static String menuImageUrl() throws IOException {
        var document = Jsoup.connect(balanDeliMenuUrl).get();
        var srcset = document.select("div.j-imageSubtitle").select("figure.cc-imagewrapper").select("img#cc-m-imagesubtitle-image-8171564654").attr("srcset");
        String[] split = srcset.split(" ");
        if (split.length < 3) {
            throw new RuntimeException("Less than expected values read from balan deli page: " + srcset);
        }
        return split[split.length - 2];
    }

    private Optional<String> doOcr(String imageUrl, boolean useMock) throws IOException, InterruptedException {
        if (useMock) {
            return Optional.of("MITTAGSKARTE\nAB 11:30 UHR\n\"Mahlzeit\" und Herzlich Willkommen!\nMontag • 07.08.2023\n• Zartes Huhnerfrikassee Erbsen Möhren Langkornreis\n• Penne al forno Champignons Erbsen Rahm Emmentaler\nDienstag • 08.08.2023\n• Schaschlik | Schwein | Paprika | Zwiebeln | Speck | Kartoffelecken\nSpinatknode cremice Gorgonzolarahmsose Rucola Parmesan\nMittwoch • 09.08.2023\n• Pan Nang Curry | Putenbrustwürfel | Wokgemüse | Duftreis | Sesam\n• Pan Nang Curry Wokgemuse Duftreis Sojasprossen Sesam\nDonnerstag 10.08.2023\n.• Gesottener Rindertafelspitz Meerrettichsoße Petersilienkartoffeln\nPanzerottiSteinoztullungruttelrahmsoseKirschtomaten\nFreitag • 11.08.2023\n• Calamari Fritti Salatbouquet Krauterdressing Remouladensoße\n• Kichererbsen-blumenkon eintoor buntes semuse gerosteter Sesam\n€ 10.90\n€ 8,40\n€ 10,90\n€ 8,40\n€ 10,90\n€ 8,40\n€ 11,80\n€ 8,40\n@ €18.80\n€ 8,40\nAlternative Gerichte zur Tageskarte\n• Wirsinacremesuppe Croutons Petersilie\n• Pasta halbgetrocknete tomaten reconnocreme\nQuiche des Tages Salatbouquet Kräutervinaigrette\n• Quiche Lorraine pecK Lauc baldwouquer Nrautervinalleure\n• Großer gemischter Marktsalat | geröstete Pinienkerne | Kräutervinaigrette\noder\n• Ceasar Salat Romanasa at Kirschtomaten Croutons Parmesan Ist\ngebratene Mannchenoruscscremen\ngegrillter Ziegenkäse\ngebratene speckstreiten\ngerostete Korner\n• Bunter Beilagensalat (\n@ €4,50\n€ 7,80\n€ 7,80\n€ 8,40\n@_ € 7,00\n€ 7,00\n€ 4.50\n€ 4,00\n€ 3,00\n€ 2,00\n€ 4,50\nFragen zu Allergenen und Inhaltsstoffen beantworten wir Ihnen gerne.");
        }
        var httpClient = HttpClient.newHttpClient();
        // https://api.ocr.space/parse/imageurl?apikey=&url=https://image.jimcdn.com/app/cms/image/transf/dimension=510x10000:format=png/path/sb74c4a44a9636c0a/image/iea90ad6b1cae21ce/version/1691400393/image.png&language=ger&OCREngine=2
        String urlWithoutApiKey = String.format("https://api.ocr.space/parse/imageurl?url=%s&language=ger&OCREngine=2", imageUrl);
        logger.atInfo().log("Ocr Api Url : %s", urlWithoutApiKey);
        URI ocrApiUrl = URI.create(String.format("%s&apikey=%s", urlWithoutApiKey, ocrApiKey));
        var response = httpClient.send(HttpRequest.newBuilder().uri(ocrApiUrl).build(), HttpResponse.BodyHandlers.ofString());
        logger.atInfo().log("Ocr Api Response Status %d", response.statusCode());
        if (is2xxStatus(response)) {
            var responseJson = mapper.readTree(response.body());
            return Optional.of(responseJson.get("ParsedResults").get(0).get("ParsedText").asText());
        }
        return Optional.empty();
    }

    private static final URI deeplApiFreeUrl = URI.create("https://api-free.deepl.com/v2/translate");

    private List<String> translateToEnglish(List<String> translationInput) throws IOException, InterruptedException {
        if (mockDeeplAPiCall) {
            return List.of("in English");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(deeplApiFreeUrl)
                .header("Authorization", String.format("DeepL-Auth-Key %s", deeplApiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(new DeeplApiRequestObject(translationInput, "DE", "EN-US", false))))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (is2xxStatus(response)) {
            var deeplResponse = mapper.readValue(response.body(), DeeplApiResponse.class);
            return deeplResponse.translations().stream().map(Translation::text).toList();
        } else {
            logger.atInfo().log("Deepl Api Call Response status : %d | body : %s", response.statusCode(), response.body());
        }

        return List.of();
    }

    private static <T> boolean is2xxStatus(HttpResponse<T> httpResponse) {
        return httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300;
    }
}

enum Weekday {
    Monday("Montag"),
    Tuesday("Dienstag"),
    Wednesday("Mittwoch"),
    Thursday("Donnerstag"),
    Friday("Freitag");

    final String inGerman;

    Weekday(String inGerman) {
        this.inGerman = inGerman;
    }

    static Optional<Weekday> find(Predicate<Weekday> predicate) {
        return Arrays.stream(Weekday.values()).filter(predicate).findAny();
    }

    @Override
    public String toString() {
        return name() + " [" + inGerman + "]";
    }
}

record DeeplApiRequestObject(List<String> text, String source_lang, String target_lang, boolean preserve_formatting) {
}

record DeeplApiResponse(List<Translation> translations) {
}

record Translation(String detected_source_language, String text) {
}

record Menu(Weekday day, String date, String one, String two) {
}