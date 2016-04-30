/**
 * 
 */
package org.cfg;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

		// List<String> urls = Lists.newArrayList("http://grants.nih.gov/grants/guide/pa-files/PA-16-206.html");
		// List<String> urls = Lists.newArrayList("http://grants.nih.gov//grants/guide/pa-files/PAR-16-195.html");
		List<String> urls = grabAllUrls();

		Path outputPath = Paths.get("./nih-data.json");
		try (BufferedWriter writer = Files.newBufferedWriter(outputPath))
			{
			for (String url : urls)
				{
				logger.info("Processing url: {}", url);
				DocTupple doc = processURL(url);
				logger.info("JSON: ===> \n{}", doc.json);
				writer.write("{\"index\" : { ");
				writer.write(String.format("\"%s\" : \"%s\" ", "_index", "nih"));
				writer.write(String.format(", \"%s\" : \"%s\" ", "_type", "grant"));
				writer.write(String.format(", \"%s\" : \"%s\" ", "_id", doc.id));
				writer.write(" } }");
				writer.newLine();
				writer.write(doc.json);
				writer.newLine();
				}
			}
		logger.info("Output written to: {}", outputPath);

		}

	private static DocTupple processURL(String url) throws Exception
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
			processKVRow(label, value, jsonContainer);

		});

		String id = jsonContainer.get("announcement_number").toString();
		String json = mapper.writeValueAsString(jsonContainer);
		return new DocTupple(id, json);
		}

	static class DocTupple
		{
		String id;
		String json;

		public DocTupple(String id, String json)
			{
			this.id = id;
			this.json = json;
			}
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
				.split(cleanString(value.text())));
		if (tokens.size() == 1)
			{
			// jsonContainer.put(key, tokens.get(0));
			logger.debug("Intentionally not adding field: {}", key);
			}
		else
			{
			// jsonContainer.put(key, tokens);
			logger.debug("Intentionally not adding field: {}", key);
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
			processSimpleText(value, jsonContainer, "title");
			}

		if (key.equals("Activity Code"))
			{
			processActivityCodes(value, jsonContainer, "activity_code");
			}

		// if (key.equals("Announcement Type"))
		// {
		// processAnnouncementNumber(value, jsonContainer, "announcement_number");
		// }

		if (key.equals("Funding Opportunity Announcement (FOA) Number"))
			{
			processAnnouncementNumber(value, jsonContainer, "announcement_number");
			}

		if (key.equals("Funding Opportunity Purpose"))
			{
			processSimpleText(value, jsonContainer, "summary");
			}

		if (key.equals("Posted Date"))
			{
			List<Date> d = extractDates(value.text());
			if (d != null && !d.isEmpty())
				{
				jsonContainer.put("posted_date", d.get(0));
				}
			}

		if (key.equals("Expiration Date"))
			{
			List<Date> d = extractDates(value.text());
			if (d != null && !d.isEmpty())
				{
				jsonContainer.put("expiration_date", d.get(0));
				}
			}

		if (key.equals("Open Date (Earliest Submission Date)"))
			{
			List<Date> d = extractDates(value.text());
			if (d != null && !d.isEmpty())
				{
				jsonContainer.put("open_date", d.get(0));
				}
			}

		// if (key.toLowerCase().endsWith(" date"))
		// {
		// String lowerKey = key.toLowerCase();
		// String newKey = lowerKey.replaceAll(" ", "_");
		// List<Date> d = extractDates(value.text());
		// if (d != null && !d.isEmpty())
		// {
		// jsonContainer.put(newKey, d.get(0));
		// }
		//
		// System.out.println("----");
		//
		// }

		}

	private static void processCPOs(Element e, Map<String, Object> jsonContainer, String newFieldLabel)
		{
		List<String> tokens = e.select("a").stream().map(ea -> cleanString(ea.text())).collect(Collectors.toList());
		if (tokens.size() > 0)
			{
			jsonContainer.put(newFieldLabel, tokens);
			}
		}

	private static void processActivityCodes(Element e, Map<String, Object> jsonContainer, String newFieldLabel)
		{
		List<String> tokens = e.select("a").stream().map(ea -> cleanString(ea.text())).collect(Collectors.toList());
		if (tokens.size() > 0)
			{
			jsonContainer.put(newFieldLabel, tokens);
			}
		}

	private static void processSimpleText(Element e, Map<String, Object> jsonContainer, String newFieldLabel)
		{
		jsonContainer.put(newFieldLabel, cleanString(e.text()));
		}

	private static void processAnnouncementNumber(Element e, Map<String, Object> jsonContainer, String newFieldLabel)
		{
		String text = cleanString(e.text());

		List<String> grantTypes = new ArrayList<>();
		if (StringUtils.startsWith(text, "PA-"))
			grantTypes.add("PA");
		if (StringUtils.startsWith(text, "PAR-"))
			grantTypes.add("PAR");
		if (StringUtils.startsWith(text, "PAS-"))
			grantTypes.add("PAS");
		if (StringUtils.startsWith(text, "RFA-"))
			grantTypes.add("RFA");
		if (StringUtils.startsWith(text, "NOT-"))
			grantTypes.add("NOT");

		if (!grantTypes.isEmpty())
			jsonContainer.put("grant_types", grantTypes);

		jsonContainer.put(newFieldLabel, text);
		}

	private static void processCDFAs(Element e, Map<String, Object> jsonContainer)
		{
		List<String> tokens = Lists.newArrayList(Splitter.on(";").omitEmptyStrings().trimResults()
				.split(cleanString(e.text())));

		if (tokens.size() > 0)
			{
			jsonContainer.put("cfdas_cds", tokens);
			}
		}

	private static String cleanString(String input)
		{
		return input.replaceAll("(\\r|\\n|\\u00a0)", "").trim();
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
