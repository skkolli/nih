/**
 * 
 */
package org.cfg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * @author skolli
 *
 */
public class HTMLParserBackup {
	
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final Logger logger = LoggerFactory.getLogger(HTMLParserBackup.class);

	public static void main(String[] args) throws Exception {

		List<String> urls = grabAllUrls();

//		for (String url: urls) {
//			System.out.println(url);
//			processURL(url);
//			System.out.println("---------------------------------------");		
//		}
		
		processURL("http://grants.nih.gov/grants/guide/pa-files/PA-16-206.html");

	}

	private static void processURL(String url) throws Exception {
		Map<String, Object> finalResult = new HashMap<>();
		Document doc = Jsoup.connect(url).get();

		finalResult.put("raw_text", doc.text());
		finalResult.put("raw_html", doc.select(".WordSection1").html());
		finalResult.put("grant_url", url);
		
		Element part1 = doc.select(":containsOwn(Part 1. Overview Information)").first();
		Element kd = doc.select("div:containsOwn(Key Dates)").first();
		Element part2 = doc.select("div:containsOwn(Part 2. Full Text of Announcement)").first();

		Elements part1Props = new Elements();
		Elements siblings = part1.siblingElements();
		List<Element> result = siblings.subList(siblings.indexOf(part1) + 1, siblings.lastIndexOf(kd));
		part1Props.addAll(result);

//		finalResult.put("Part 1. Overview Information", buildProps(part1Props));
		finalResult.putAll(buildProps(part1Props));
		
		Elements kdProps = new Elements();
		Elements kdSiblings = part1.siblingElements();
		List<Element> kdPropElements = kdSiblings.subList(kdSiblings.indexOf(kd) + 1, kdSiblings.lastIndexOf(part2));
		kdProps.addAll(kdPropElements);

//		finalResult.put("Key Dates", buildProps(kdProps));
		finalResult.putAll(buildProps(kdProps));

		Element section1 = doc.select("div:containsOwn(Funding Opportunity Description)").first();
		Element section2 = doc.select("div:containsOwn(Award Information)").first();
		Element section3 = doc.select("div:containsOwn(Eligibility Information)").first();
		Element section4 = doc.select(":containsOwn(Application and Submission Information)").first();
		Element section5 = doc.select(":containsOwn(Application Review Information)").first();
		Element section6 = doc.select(":containsOwn(Award Administration Information)").first();
		Element section7 = doc.select(":containsOwn(Agency Contacts)").first();

		Elements awardProps = new Elements();
		Elements awardSiblings = section1.siblingElements();
		List<Element> awardPropElements = awardSiblings.subList(awardSiblings.indexOf(section2) + 1,
				awardSiblings.lastIndexOf(section3));
		awardProps.addAll(awardPropElements);
		
//		finalResult.put("Award Information", buildProps(awardProps));
		finalResult.putAll(buildProps(awardProps));
		
		logger.info("JSON: ===> {}", mapper.writeValueAsString(finalResult));
	}

	private static Map<String, Object> buildProps(Elements elements) {
		Map<String, Object> result = new HashMap<>();
		Elements rows = elements.select(".row");
		rows.iterator().forEachRemaining(e -> {
			//Trim and remove &nbsp;
			String k = e.select(".datalabel").text().trim().replace("\u00a0", "");
			Elements pElements = e.select(".datacolumn > p");
			if (pElements.size() > 1) {
				List<String> vals = new ArrayList<>();
				pElements.forEach(pe -> {					
					vals.add(pe.text().trim());
				});
				
				result.put(k, vals);
			} else if (pElements.size() == 1) {
				result.put(k, pElements.text().trim());	
			} else {
				result.put(k, e.select(".datacolumn").text().trim());
			}
			
			//TODO: If we want, we can look for <ul><li> and convert them to an array in JSON.
			//TODO: If we want, we can look for <br> tags in <p> and convert them to an array in JSON.
			
		});

		return result;
	}

	private static List<String> grabAllUrls() throws Exception {
		String url = "http://grants.nih.gov/funding/searchguide/nih-guide-to-grants-and-contracts.cfm?start=26&Activity_Code=&Expdate_On_After=&OrderOn=RelDate&OrderDirection=DESC&NoticesToo=0&OpeningDate_On_After=&maxreldate=2016-04-27%2009:25:33.0&Parent_FOA=All&PrimaryICActive=Any&RelDate_On_After=&Status=1&SearchTerms=&PAsToo=1&RFAsToo=1&TitleText=&AppPackage=Any&Activity_Code_Groups=&Include_Sponsoring=1&SearchTermOperator=Logical_OR";
		Document doc = Jsoup.connect(url).get();
		Elements elements = doc.select(".results-total-hitsclass h3 a");
		List<String> urls = new ArrayList<>();

		elements.iterator().forEachRemaining(e -> urls.add("http://grants.nih.gov/" + e.attr("href")));

		// System.out.println(urls);
		return urls;
	}

}
