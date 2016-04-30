/**
 * 
 */
package org.cfg;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.ocpsoft.prettytime.shade.org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * @author skolli
 *
 */
public class HTMLParser
	{

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final Logger logger = LoggerFactory.getLogger(HTMLParser.class);

	public static void main(String[] args) throws Exception
		{
		// ES 'basic_date' - yyyyMMdd
		DateFormat df = new SimpleDateFormat("yyyyMMdd");
		mapper.setDateFormat(df);

		// List<String> urls = grabAllUrls();

		// for (String url: urls) {
		// System.out.println(url);
		// processURL(url);
		// System.out.println("---------------------------------------");
		// }

		processURL("http://grants.nih.gov/grants/guide/pa-files/PA-16-206.html");

		}

	private static void processURL(String url) throws Exception
		{
		Map<String, Object> jsonContainer = new HashMap<>();
		Document doc = Jsoup.connect(url).get();

		jsonContainer.put("raw_text", doc.text());
		jsonContainer.put("raw_html", doc.select(".WordSection1").first().parent().html());
		jsonContainer.put("grant_url", url);

		Elements rows = doc.select(".row");
		rows.forEach(r -> {
		Element label = r.select(".datalabel").first();
		Element value = r.select(".datacolumn").first();

		if (label != null && value != null)
			{
			processKVRow(label, value, jsonContainer);
			}

		});

		String json = mapper.writeValueAsString(jsonContainer);
		logger.info("JSON: ===> \n{}", json);
		}

	private static void processKVRow(Element label, Element value, Map<String, Object> jsonContainer)
		{

		for (Element brtag : value.select("br"))
			{
			brtag.after(new TextNode("\u0001", ""));
			}

		for (Element brtag : value.select("p"))
			{
			brtag.after(new TextNode("\u0001", ""));
			}

		String key = label.text().trim().replace("\u00a0", "");

		List<String> tokens = Lists.newArrayList(Splitter.on("\u0001").omitEmptyStrings().trimResults()
				.split(value.text()));
		if (tokens.size() == 1)
			{
			// jsonContainer.put(key, tokens.get(0));
			logger.info("Intentionally not adding field: {}", key);
			}
		else
			{
			// jsonContainer.put(key, tokens);
			logger.info("Intentionally not adding field: {}", key);
			}

		if (key.equals("Components of Participating Organizations"))
			{
			processCPOs(value, jsonContainer, "cpo_cds");
			}

		if (key.equals("Catalog of Federal Domestic Assistance (CFDA) Number(s)"))
			{
			processCDFAs(value, jsonContainer);
			}

		if (key.equals("Participating Organization(s)"))
			{
			processCPOs(value, jsonContainer, "po_cds");
			}

		if (key.equals("Funding Opportunity Title"))
			{
			processTitle(value, jsonContainer, "title");
			}

		if (key.equals("Activity Code"))
			{
			processActivityCodes(value, jsonContainer, "activity_code");
			}

		if (key.equals("Announcement Type"))
			{
			processAnnouncementNumber(value, jsonContainer, "announcement_number");
			}

		if (key.toLowerCase().endsWith(" date"))
			{
			String lowerKey = key.toLowerCase();
			System.out.println(lowerKey);
			String newKey = lowerKey.replaceAll(" ", "_");
			System.out.println(newKey);
			List<Date> d = extractDates(value.text());
			if (d != null && !d.isEmpty())
				{
				jsonContainer.put(newKey, d.get(0));
				}

			System.out.println("----");

			}

		// if (key.toLowerCase().contains(" date(s)")) {
		// String lowerKey = key.toLowerCase();
		// System.out.println(lowerKey);
		// String newKey = lowerKey.substring(0, lowerKey.indexOf(" date(s)") +
		// 5).replaceAll("[()]", "").replaceAll(" ", "_");
		// System.out.println(newKey);
		// List<Date> d = extractDates(value.text());
		// if (d != null && !d.isEmpty()) {
		// jsonContainer.put(newKey, d);
		// }
		//
		// }

		}

	private static void processCPOs(Element e, Map<String, Object> jsonContainer, String newFieldLabel)
		{
		List<String> tokens = e.select("a").stream().map(ea -> ea.text()).collect(Collectors.toList());
		if (tokens.size() > 0)
			{
			jsonContainer.put(newFieldLabel, tokens);
			}
		}

	private static void processActivityCodes(Element e, Map<String, Object> jsonContainer,
			String newFieldLabel)
		{
		List<String> tokens = e.select("a").stream().map(ea -> ea.text()).collect(Collectors.toList());
		if (tokens.size() > 0)
			{
			jsonContainer.put(newFieldLabel, tokens);
			}
		}

	private static void processTitle(Element e, Map<String, Object> jsonContainer,
			String newFieldLabel)
		{
		jsonContainer.put(newFieldLabel, e.text());
		}

	private static void processAnnouncementNumber(Element e, Map<String, Object> jsonContainer,
			String newFieldLabel)
		{
		List<String> tokens = e.select("a").stream().map(ea -> ea.text()).collect(Collectors.toList());
		if (tokens.size() > 0)
			{
			tokens.stream().forEach(tok -> {
			if (StringUtils.startsWith(tok, "PA-"))
				jsonContainer.put("is_pa", true);
			if (StringUtils.startsWith(tok, "PAR-"))
				{
				jsonContainer.put("is_pa", true);
				jsonContainer.put("is_par", true);
				}
			if (StringUtils.startsWith(tok, "PAS-"))
				{
				jsonContainer.put("is_pa", true);
				jsonContainer.put("is_pas", true);
				}
			if (StringUtils.startsWith(tok, "RFA-"))
				jsonContainer.put("is_rfa", true);
			});

			jsonContainer.put(newFieldLabel, tokens);
			}
		}

	private static void processCDFAs(Element e, Map<String, Object> jsonContainer)
		{
		List<String> tokens = Lists.newArrayList(Splitter.on(";").omitEmptyStrings().trimResults()
				.split(e.text()));

		if (tokens.size() > 0)
			{
			jsonContainer.put("cfdas_cds", tokens);
			}
		}

	private static List<String> grabAllUrls() throws Exception
		{
		String url = "http://grants.nih.gov/funding/searchguide/nih-guide-to-grants-and-contracts.cfm?start=26&Activity_Code=&Expdate_On_After=&OrderOn=RelDate&OrderDirection=DESC&NoticesToo=0&OpeningDate_On_After=&maxreldate=2016-04-27%2009:25:33.0&Parent_FOA=All&PrimaryICActive=Any&RelDate_On_After=&Status=1&SearchTerms=&PAsToo=1&RFAsToo=1&TitleText=&AppPackage=Any&Activity_Code_Groups=&Include_Sponsoring=1&SearchTermOperator=Logical_OR";
		Document doc = Jsoup.connect(url).get();
		Elements elements = doc.select(".results-total-hitsclass h3 a");
		List<String> urls = new ArrayList<>();

		elements.iterator().forEachRemaining(e -> urls.add("http://grants.nih.gov/" + e.attr("href")));

		return urls;
		}

	private static List<Date> extractDates(String text)
		{
		List<Date> dates = new PrettyTimeParser().parse(text);
		return dates;
		}
	}
