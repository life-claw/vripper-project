package tn.mnlr.vripper.host;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import tn.mnlr.vripper.exception.HostException;
import tn.mnlr.vripper.exception.HtmlProcessorException;
import tn.mnlr.vripper.exception.XpathException;
import tn.mnlr.vripper.q.ImageFileData;
import tn.mnlr.vripper.services.ConnectionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImxHost extends Host {

    private static final Logger logger = LoggerFactory.getLogger(ImxHost.class);

    private static final String host = "imx.to";
    private static final String CONTINUE_BUTTON_XPATH = "//*[@name='imgContinue']";
    private static final String IMG_XPATH = "//img[@class='centred']";

    @Autowired
    private ConnectionManager cm;

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getLookup() {
        return host;
    }

    @Override
    protected void setNameAndUrl(final String _url, final ImageFileData imageFileData, final HttpClientContext context) throws HostException {

        String url = _url.replace("http://", "https://");
        Response resp = getResponse(url, context);
        Document doc = resp.getDocument();

        Node contDiv;
        String value = null;
        try {
            logger.debug(String.format("Looking for xpath expression %s in %s", CONTINUE_BUTTON_XPATH, url));
            contDiv = xpathService.getAsNode(doc, CONTINUE_BUTTON_XPATH);
            Node node = contDiv.getAttributes().getNamedItem("value");
            if (node != null) {
                value = node.getTextContent();
            }
        } catch (XpathException e) {
            throw new HostException(e);
        }

        if (value == null) {
            throw new HostException("Failed to obtain value attribute from continue input");
        }
        logger.debug(String.format("Click button found for %s", url));
        HttpClient client = cm.getClient().build();
        HttpPost httpPost = cm.buildHttpPost(url);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("imgContinue", value));
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(params));
        } catch (Exception e) {
            throw new HostException(e);
        }
        logger.debug(String.format("Requesting %s", httpPost));
        try (CloseableHttpResponse response = (CloseableHttpResponse) client.execute(httpPost, context)) {
            logger.debug(String.format("Cleaning response for %s", httpPost));
            doc = htmlProcessorService.clean(EntityUtils.toString(response.getEntity()));
            EntityUtils.consumeQuietly(response.getEntity());
        } catch (IOException | HtmlProcessorException e) {
            throw new HostException(e);
        }

        Node imgNode;
        try {
            logger.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url));
            imgNode = xpathService.getAsNode(doc, IMG_XPATH);
        } catch (XpathException e) {
            throw new HostException(e);
        }

        try {
            logger.debug(String.format("Resolving name and image url for %s", url));
            String imgTitle = imgNode.getAttributes().getNamedItem("alt").getTextContent().trim();
            String imgUrl = imgNode.getAttributes().getNamedItem("src").getTextContent().trim();

            imageFileData.setImageUrl(imgUrl);
            imageFileData.setImageName(imgTitle.isEmpty() ? imgUrl.substring(imgUrl.lastIndexOf('/') + 1) : imgTitle);
        } catch(Exception e) {
            throw new HostException("Unexpected error occurred", e);
        }
    }
}
