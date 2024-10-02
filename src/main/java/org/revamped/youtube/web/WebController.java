package org.revamped.youtube.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.revamped.youtube.resilience.RetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class WebController {

    /** Docs:
     * https://github.com/iv-org/invidious/blob/53e8a5d62d4d7d66e8819f221a1c7b168102995c/src/invidious/yt_backend/youtube_api.cr#L344
     */

    @Autowired
    YoutubeCrawler youtubeCrawler;

    @GetMapping("/search")
    HtmxResponse search(@RequestParam String query, Model model) {
        // We pass the search query, scrape the search results and show it to the user
        // And we click on an interested item, It'll pass the videoId of that item to the search box
        // And the Search box will pull that particular video to play YT videos without any ads
        model.addAttribute("videos",youtubeCrawler.getVideos(query));
        return HtmxResponse.builder()
                .view("index :: ytVideos")
                .build();
    }

    @GetMapping("/watch/{v}")
    HtmxResponse watch(@PathVariable("v") String v, Model model) {
        YouTubeResponseDTO dto = new YouTubeResponseDTO(null, v, null);
        model.addAttribute("video", dto);
        return HtmxResponse.builder()
                .view("yt :: ytPlayer")
                .build();
    }
}

record YouTubeResponseDTO (String title, String url, String thumbnailUrl) {}

@Service
class YoutubeCrawler {

    private static final Logger log = LoggerFactory.getLogger(YoutubeCrawler.class);
    private static final Pattern polymerInitialDataRegex = Pattern.compile("(window\\[\"ytInitialData\"]|var ytInitialData)\\s*=\\s*(.*);");

    List<YouTubeResponseDTO> getVideos(String searchQuery) {
        Content content = crawlSearchResults(searchQuery);

        List<Content> contentList = new ArrayList<>(
                content
                        .twoColumnSearchResultsRenderer
                        .primaryContents
                        .sectionListRenderer
                        .contents.getFirst().itemSectionRenderer.contents
        );

        ArrayList<YouTubeResponseDTO> youTubeResponseDTOS = new ArrayList<>(contentList.size());

        contentList.stream()
                .filter(content1 -> content1.videoRenderer() != null)
                .forEach(
                        content1 -> youTubeResponseDTOS.add(
                                new YouTubeResponseDTO(
                                        content1.videoRenderer().title().runs().getFirst().text,
                                        content1.videoRenderer().videoId,
                                        content1.videoRenderer().thumbnail.thumbnails.getFirst().url)
                        )
                );
        return youTubeResponseDTOS;
    }

    private Content crawlSearchResults(String searchQuery) {
        RetryService<Content> retryService = new RetryService<>();

        return retryService.retry(() -> {
            Document document = Jsoup.connect("https://www.youtube.com/results?search_query=" + searchQuery)
                    .get();

            // log.info(document.outerHtml());
            // document.getElementsByTag("a").forEach(System.out::println); // This will get all links in the document
            // Match the JSON from the HTML. It should be within a script tag
            // String matcher0 = matcher.group(0);
            // String matcher1 = matcher.group(1);
            // String matcher2 = matcher.group(2);
            Matcher matcher = polymerInitialDataRegex.matcher(document.getElementsByTag("script").outerHtml());
            if (!matcher.find()) {
                log.warn("Failed to match ytInitialData JSON object");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(matcher.group(2));

            JsonNode contents = jsonNode.get("contents");
            return Objects.requireNonNull(objectMapper.treeToValue(contents, Content.class));
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(
            ItemSectionRenderer itemSectionRenderer,
            VideoRenderer videoRenderer,
            TwoColumnSearchResultsRenderer twoColumnSearchResultsRenderer
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemSectionRenderer(
            ArrayList<Content> contents
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VideoRenderer(
            String videoId,
            Title title,
            Thumbnail thumbnail
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Thumbnail(
            ArrayList<Thumbnails> thumbnails
    ){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Thumbnails(
            String url,
            String width,
            String height
    ){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TwoColumnSearchResultsRenderer(
            PrimaryContents primaryContents
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PrimaryContents(
            SectionListRenderer sectionListRenderer
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SectionListRenderer(
            ArrayList<Content> contents
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Title(
            ArrayList<Run> runs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Run(
            String text
    ) {
    }
}
