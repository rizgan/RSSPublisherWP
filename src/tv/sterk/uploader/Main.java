package tv.sterk.uploader;

import net.bican.wordpress.*;
import net.bican.wordpress.exceptions.FileUploadException;
import net.bican.wordpress.exceptions.InsufficientRightsException;
import net.bican.wordpress.exceptions.InvalidArgumentsException;
import net.bican.wordpress.exceptions.ObjectNotFoundException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import redstone.xmlrpc.XmlRpcFault;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Main {

    public static Wordpress wp;
    static String WP_LOGIN;
    static String WP_PASSWORD;
    static String WP_URL;
    static String FROM_SITE_URL;
    static String FIND_ARTICLE_URL;
    static String ARTICLE_TITLE;
    static String ARTICLE_IMAGE;
    static String ARTICLE_TEXT;
    static int PUBLISH_TO_CATEGORY_IN_WP;
    static String PUBLISH_WITH_TAG;
    static String TMP_FOLDER_NAME;
    static String JAR_PATH_WITHOUT_FILE_NAME;

    public static void readConfig() throws URISyntaxException {
        Properties prop = new Properties();
        BufferedReader br = null;

        try {
            String jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()) + "";
            JAR_PATH_WITHOUT_FILE_NAME = jarPath.replace(jarPath.substring(jarPath.lastIndexOf("\\") + 1), "");

            br = new BufferedReader(new InputStreamReader(new FileInputStream(JAR_PATH_WITHOUT_FILE_NAME + "config.properties"), "UTF-8"));

            prop.load(br);

            // get the property value
            WP_LOGIN = prop.getProperty("wpLogin");
            WP_PASSWORD = prop.getProperty("wpPassword");
            WP_URL = prop.getProperty("wpUrl");
            FROM_SITE_URL = prop.getProperty("fromSiteUrl");
            FIND_ARTICLE_URL = prop.getProperty("findArticleUrl");
            ARTICLE_TITLE = prop.getProperty("articleTitle");
            ARTICLE_IMAGE = prop.getProperty("articleImage");
            ARTICLE_TEXT = prop.getProperty("articleText");
            PUBLISH_TO_CATEGORY_IN_WP = Integer.parseInt(prop.getProperty("publishToCategoryInWP"));
            PUBLISH_WITH_TAG = prop.getProperty("publishWithTag");
            TMP_FOLDER_NAME = prop.getProperty("tmpFolderName");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static Term findOrCreateTerm(final String taxonomyName, final String name) throws InsufficientRightsException, InvalidArgumentsException, XmlRpcFault {
        return wp.getTerms(taxonomyName).stream().filter(p -> p.getName().equals(name)).findFirst()
                .orElseGet(() -> {
                    final Term t = new Term();
                    t.setTaxonomy(taxonomyName);
                    t.setName(name);
                    try {
                        final Integer p = wp.newTerm(t);
                        return wp.getTerm(taxonomyName, p);
                    } catch (InsufficientRightsException | InvalidArgumentsException | XmlRpcFault | ObjectNotFoundException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
    }

    public static void testGetTermsWithFilter() throws Exception {
        final TermFilter filter = new TermFilter();
        filter.setSearch("kuzey");
        List<Term> terms = wp.getTerms("category", filter);
        System.out.println("---" + terms.get(0) + "---");
    }

    public static void main(String[] args) throws Exception {

        readConfig();

        wp = new Wordpress(WP_LOGIN, WP_PASSWORD, WP_URL);
        FilterPost filter = new FilterPost();
        filter.setNumber(100);
        final List<Post> recentPosts = wp.getPosts(filter);
        boolean postIsAlreadyInDataBase = false;

//        testGetTermsWithFilter();

        Document doc = Jsoup.connect(FROM_SITE_URL).timeout(3000).userAgent("Mozilla").get();

        Element links = doc.select(FIND_ARTICLE_URL).first(); // a with href
        Element link = links.select("a").first();
        String articleUrl = link.attr("href");

        Document article = Jsoup.connect(articleUrl).get();

        Element articleTitlePlace = article.select(ARTICLE_TITLE).first(); // a with href

        Element articleImagePlace = article.select(ARTICLE_IMAGE).first();

        Element articleTextPlace = article.select(ARTICLE_TEXT).first();

        String articleTitleText = articleTitlePlace.text().replace("ANF | ", "");
        String articleImageUrl = articleImagePlace.absUrl("src").replace("https", "http");
        String articleText = articleTextPlace.html().replace("<div style=\"width: 100%;height: 0;padding-bottom: 56.25%\">", "");

        Taxonomy c = wp.getTaxonomy("post_tag");
//        System.out.printf(c.getCap().getEdit_terms() + "");

        final Term term1 = wp.getTerm("category", PUBLISH_TO_CATEGORY_IN_WP);
        Term term2 = findOrCreateTerm("post_tag", PUBLISH_WITH_TAG);
        term2.setTaxonomy(c.getName());

        int lastSlashiName = articleImageUrl.lastIndexOf("/");
        String imageFileName = articleImageUrl.substring(lastSlashiName + 1);
        imageFileName = imageFileName.replace("\\", "-").replace("/", "-").replace(":", "-").replace("*", "-").replace("?", "-").replace("\"", "-").replace("<", "-").replace(">", "-").replace("|", "-").replace(" ", "_");

        for (final Post page : recentPosts) {

            if (articleTitleText.contains(page.getPost_title())) {
                postIsAlreadyInDataBase = true;
                System.out.println("Already there!!!");
                break;
            }
        }
        if ((articleImageUrl.length() != 0) && (postIsAlreadyInDataBase == false)) {

            Connection.Response resultImageResponse = Jsoup.connect(articleImageUrl).ignoreContentType(true).execute();
            FileOutputStream out = (new FileOutputStream(new java.io.File(JAR_PATH_WITHOUT_FILE_NAME + TMP_FOLDER_NAME + "\\" + imageFileName)));
            out.write(resultImageResponse.bodyAsBytes());  // resultImageResponse.body() is where the image's contents are.

            File file = new File(JAR_PATH_WITHOUT_FILE_NAME + TMP_FOLDER_NAME + "\\" + imageFileName);

            try (InputStream media = new FileInputStream(file)) {

                MediaItemUploadResult mediaUploaded = wp.uploadFile(media, articleTitleText.replace("\\", "-").replace("/", "-").replace(":", "-").replace("*", "-").replace("?", "-").replace("\"", "-").replace("<", "-").replace(">", "-").replace("|", "-").replace(" ", "_") + ".jpg");
                MediaItem r = wp.getMediaItem(mediaUploaded.getId());

                Post recentPost = new Post();

//                findOrCreateTerm("post_tag", "manşet");

                recentPost.setPost_thumbnail(r);
                recentPost.setTerms(Arrays.asList(new Term[]{term1, term2}));
                recentPost.setPost_title(articleTitleText);
                recentPost.setPost_content(articleText);
                recentPost.setPost_status("publish");
                Integer result = wp.newPost(recentPost);
                out.close();

                Runtime rt = Runtime.getRuntime();
                Process proc = rt.exec("cmd /c del " + JAR_PATH_WITHOUT_FILE_NAME + TMP_FOLDER_NAME + "\\*.* /s /q");
            }
        }
    }
}