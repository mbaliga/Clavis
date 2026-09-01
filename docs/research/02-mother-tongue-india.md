# Is the "mother tongue" really the weaker tongue? An evidence review for urban India

*Research memo, 29 August 2026. Prepared for a Hyderabad-based product designer building a multi-language fluency / language-maintenance app.*

**The claim under test:** "Languages that people claim as their 'mother tongue' are languages in which they have a much smaller vocabulary and fluency than they do in English. In most cases in India, anyone I have met over the course of my education and work has claimed a language other than English as their mother tongue and has failed to demonstrate the same command over it as over English."

**Evidence grading used below:** **[Strong]** = large-N official data or peer-reviewed studies with controls; **[Mixed]** = smaller samples, self-report, or single-site studies; **[Anecdotal]** = journalism, expert quotes, single cases. **[K]** = figure drawn from the analyst's background knowledge of the cited publication; a live fetch of that source was blocked in this session (search budget exhausted after ~25 queries; only URLs surfaced by search could be fetched). Verify [K] figures before quoting them externally.

---

## 0. Bottom line

**Partially validated — and the part that is validated is narrower and more interesting than the claim as stated.**

- **Supported (narrow form):** Among urban, English-medium-educated Indians (the designer's entire sample), command of the *formal/academic register* and of *script literacy* in the declared mother tongue is typically weaker than in English, and vocabulary for adult, professional, and abstract domains lives mainly in English. This is exactly what the best Indian survey on the topic (IIT Bombay, n = 1,375) and heritage-language theory predict.
- **Not supported (broad form):** "Most cases in India." Only ~6–11% of Indians speak English at all; for the other ~90%, the mother tongue is unambiguously the stronger language in every skill. Even within the English-speaking minority, *everyday oral fluency* in the mother tongue is generally intact — nobody in the IIT Bombay sample spoke English to their parents.
- **Best explanation:** mostly (b) domain/register asymmetry plus (c) sampling bias — the designer meets people in education and work, the two domains India has ceded to English — with (a) genuine attrition true for a growing sub-group (children of English-speaking homes, interstate migrants, and anyone who never became literate in the mother tongue script).
- **For the app:** the target user is a *heritage speaker*, not a beginner. Build for script literacy, formal register, and adult-domain vocabulary on top of intact listening/speaking — not for "hello, my name is."

---

## 1. What "mother tongue" means in the Indian Census, and who actually speaks English

### 1.1 The Census definition is identity, not proficiency **[Strong]**

The Census defines mother tongue as the language spoken in childhood by the person's mother (or, if she died in infancy, the language mainly spoken in the household). It is recorded **as declared, without any test**. The Registrar General's own caveat: the data reflect "the designation provided by the respondents of the linguistic mediums in which the respondents think they communicate" and "need not be identical with the actual linguistic mediums" ([Deccan Chronicle, citing the RGI language release](https://www.deccanchronicle.com/nation/in-other-news/010718/over-19500-languages-spoken-in-india-as-mother-tongue-census.html)). So when someone "claims" a mother tongue, they are using the term exactly as the Indian state does — as an ancestry/identity label. The designer's observation that the claim and the competence diverge is therefore not a contradiction; it is what the definition permits.

### 1.2 Census 2011 headline numbers **[Strong]**

- **19,569 raw mother-tongue returns → 1,369 "rationalised" mother tongues → 121 languages** (each with ≥10,000 speakers): **22 Scheduled + 99 non-Scheduled** (English is one of the 99). **96.71%** of the population returned a Scheduled language ([The India Forum](https://www.theindiaforum.in/article/what-census-obscures); [Deccan Chronicle](https://www.deccanchronicle.com/nation/in-other-news/010718/over-19500-languages-spoken-in-india-as-mother-tongue-census.html)).
- Rationalisation is itself identity politics: 54 mother tongues are folded into "Hindi", including Bhojpuri (50.6 m), Rajasthani (25.8 m) and Chhattisgarhi (16.2 m); 18.6 m people fall into "other mother tongues" ([The India Forum](https://www.theindiaforum.in/article/what-census-obscures)). Language tables from the 2011 Census took roughly seven years to publish ([Shankar IAS summary](https://www.shankariasparliament.com/article/language-data-of-2011-census)).
- Shares of population by mother tongue (Census 2011, Statement 1; figures widely reproduced): Hindi 43.63%, Bengali 8.03%, Marathi 6.86%, Telugu 6.70%, Tamil 5.70%, Gujarati 4.58%, Urdu 4.19%, Kannada 3.61%, Odia 3.10%, Malayalam 2.88%, Punjabi 2.74% [K].
- **English:** first language **259,678 (0.02%)**; second language **~83.1 m**; third language **~46.0 m**; **total ~129.3 m ≈ 10.6–10.7%** of the 2011 population ([Wikipedia compilation of Census 2011](https://en.wikipedia.org/wiki/List_of_countries_by_English-speaking_population); [Imminent/Translated](https://imminent.translated.com/india-do-you-speak-english); [thehistoryofenglish.com](https://www.thehistoryofenglish.com/how-many-people-in-india-speak-english) reports slightly lower L2/L3 counts, 128.5 m total). Note that "second language" in the Census is also self-declared and untested.
- **Bilingualism:** 314.9 m (26%) bilingual, ~7% trilingual in 2011 ([Wikipedia, Multilingualism in India](https://en.wikipedia.org/wiki/Multilingualism_in_India)). Bilingualism in Scheduled languages rose from 12.2% (1971) to 24.8% (2011); among non-Hindi mother-tongue speakers it rose from 16% to 35.8% ([IGIDR WP-2020-015](http://www.igidr.ac.in/pdf/publication/WP-2020-015.pdf)).

### 1.3 Census 2021/2027 status **[Strong]**

The 2021 Census never happened. Census 2027 is under way: Phase I (houselisting) April–September 2026, with a self-enumeration option; Phase II (population enumeration, 40 questions including caste, "mother tongue and other languages known") in February 2027; reference moment 00:00 hrs, 1 March 2027 ([PIB backgrounder, April 2026](https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/apr/doc2026425856601.pdf); [ANI, Aug 2026](https://aninews.in/news/national/general-news/census-2027-centre-notifies-40-questions-caste-enumeration-to-take-place-1st-time-since-independence20260814143258/)). Given the 2011 lag, fresh language tables are unlikely before the end of the decade — so all "current" language data below are either 15 years old or from sample surveys.

### 1.4 Sample surveys: English is small and class-skewed **[Strong]**

- **Lok Foundation / Oxford / CMIE survey, reported by Mint (May 2019):** only **6%** of respondents speak English; **urban 12% vs rural 3%**; **richest 41% vs poorest 2%**; upper castes three times as likely as SC/ST ([summary citing Financial Express](https://www.thehistoryofenglish.com/how-many-people-in-india-speak-english); the original Mint piece, "In India, who speaks in English, and where?", could not be fetched in this session).
- **IHDS 2005 (Azam, Chin & Prakash 2013, *EDCC*):** about 4% of men fluent in English and ~16% with "some" English; fluent English carried a **~34% hourly-wage premium**, "some" English **~13%**, controlling for education [K]; the wage-return literature is summarised in [Springer, *Language Diversity and Economic Consequences in India*](https://link.springer.com/chapter/10.1007/978-3-031-88240-1_20).
- **Pew 2021 "Religion in India"** (fielded in 17 languages) did not measure proficiency but shows how identity-laden language is: 59% of Hindus say being able to speak Hindi is very important to being "truly Indian" — 87% in the Central region vs **27% in the South** ([Pew full report PDF](https://www.pewresearch.org/religion/wp-content/uploads/sites/7/2021/06/PF_06.29.21_India.full_.report.pdf)).
- NFHS and PLFS record the interview language but do not test language proficiency, so they cannot arbitrate this question [K].

**Implication for the claim:** whatever is true of the designer's colleagues, ~90% of Indians do not speak English at all. The broad version of the claim ("in most cases in India") is refuted by the base rates.

---

## 2. The pipeline: English-medium schooling

### 2.1 Growth **[Strong]**

- DISE: English-medium enrolment **1.5 crore (2008-09) → 2.9 crore (2013-14)**, nearly doubling, vs Hindi-medium 8.3 → 10.4 crore; growth was sharpest in Hindi-belt states (Bihar +47%) ([Zee News on DISE data](https://zeenews.india.com/news/india/student-enrolment-in-english-medium-schools-doubles-in-5-years_1803296.html)).
- NSO 75th round: share of 6–14-year-olds in English-medium schools **12% (2007-08) → 23% (2017-18)** ([IGIDR](http://www.igidr.ac.in/pdf/publication/WP-2020-015.pdf)); by level in 2017-18: primary 22.3%, secondary 20.9%, higher secondary 29%; **over half of Tamil-, Malayalam-, Kannada- and Punjabi-speaking pre-primary children were already in English medium** ([The Quint on NSO 2017-18](https://www.thequint.com/news/education/percentage-of-english-medium-students-rises-between-kg-to-class-10)).
- UDISE 2019-20: nationally **26% English-medium vs 42% Hindi-medium**; **Telangana 73.8%**, Kerala ~65%, Tamil Nadu 57.6% (up from 42.3% in 2014-15), Karnataka 46.5% (the only southern state where the vernacular still led), J&K ~100% ([The Federal](https://thefederal.com/education/preference-of-english-medium-instruction-rises-in-south-india-except-karnataka); [Asianet Newsable](https://newsable.asianetnews.com/india/english-becomes-most-preferred-medium-of-instruction-in-all-southern-states-except-karnataka-study-ycb-qvpjq7)).
- ASER 2024: rural private-school share **18.7% (2006) → 33.2% (2024)** ([Ideas for India](https://www.ideasforindia.in/topics/human-development/aser-2024-more-than-a-post-pandemic-recovery-in-learning)).

### 2.2 Telangana and Andhra Pradesh: the state itself switched **[Strong]**

- **Andhra Pradesh:** November 2019 order converting all government schools (Classes I–VI/VIII) to English medium from 2020-21; the High Court quashed it (Article 21-A), the Supreme Court refused interim relief and the case remains pending; in June 2021 all degree colleges were ordered to English medium. Officials cited "60–70% of Telugu-medium students opting for English at higher levels" and "more than 90% of parents okay with English medium"; of 2.62 lakh degree entrants in 2020-21, only 65,981 came from Telugu medium ([ThePrint](https://theprint.in/india/education/no-more-telugu-medium-in-andhra-why-jagan-govt-wants-english-in-all-schools-colleges/680372/); [Deccan Herald, Nov 2019](https://www.deccanherald.com/india/soon-ap-will-have-no-more-telugu-medium-govt-schools-773901.html)).
- **Telangana:** all government schools went English-medium from 2022-23 with bilingual textbooks; at undergraduate level (DOST 2023-24, first phase) **93.55% chose English medium vs 5.77% Telugu**, from 90.89%/8.26% a year earlier ([The Federal](https://thefederal.com/states/south/telangana/telangana-telugu-medium-takes-a-hit-as-students-switch-to-english-at-ug-level)). Private-school enrolment rose from 35.1 lakh (2021-22) to 44.3 lakh (2023-24) while government enrolment fell from 33.0 to 27.8 lakh ([Telangana Today on UDISE+](https://telanganatoday.com/parents-shun-telangana-government-schools-and-colleges-prefer-private-institutions-udise-report)).

For the designer this matters: Hyderabad is the epicentre of the phenomenon he describes. A Telangana-born professional under ~35 has a >70% chance of having been schooled in English, and a >90% chance of having done a degree in English.

### 2.3 What NEP 2020 says and what the evidence shows **[Strong]**

NEP 2020 §4.11: "wherever possible, the medium of instruction until at least Grade 5, but preferably till Grade 8 and beyond, will be the home language/mother tongue/local language/regional language." The policy text asserts that children grasp concepts faster in their home language but cites no studies; private schools get only a non-binding recommendation, and the policy is silent on families' demand for English ([Anhaya Foundation close reading](https://www.anhayafoundation.com/post/what-nep-2020-actually-says-about-language-and-literacy-and-what-it-does-not-say)). The evidence behind the rationale is nonetheless real:

- **Young Lives (Andhra Pradesh):** with value-added models controlling for ability, household and aspirations, Telugu-medium primary pupils outperform English-medium pupils in maths; early English medium "may negatively affect learning outcomes" ([Young Lives](https://www.younglives.org.uk/publications/does-medium-instruction-affect-learning-outcomes)).
- **Holla (ISI ACEGD 2023), ASER 2018 (n ≈ 362,000) with instrumental variables:** mother-tongue instruction raises reading by 0.32 points (+12%) and maths by 0.47 (+20%); Odisha's tribal multilingual pilot (2008-13) in a difference-in-differences design: reading +7%, maths +9%, mechanism = attendance ([paper](https://www.isid.ac.in/~acegd/acegd2023/papers/ChinmayaHolla.pdf)).
- **Vogelzang et al. 2025, *TESOL Quarterly* (Delhi, Hyderabad, Patna; 1,272 Year-4 children from low-SES schools):** English reading comprehension averaged **11% correct** vs 59–69% in the regional language, with decoding only weakly correlated with comprehension (r ≈ 0.30–0.36) ([open-access PDF](https://kops.uni-konstanz.de/bitstreams/386b570f-40cb-4a1d-89be-62b50cf0c72d/download)).
- **UNICEF Chhattisgarh language mapping (>400,000 Grade 1 pupils, 30,000 teachers):** three-quarters of pupils and nearly half of teachers had only "minimal or functional" understanding of the medium of instruction ([UNICEF India](https://www.unicef.org/india/stories/children-learn-best-when-theyre-taught-their-mother-tongue)).
- Tamil Nadu's shift of 300,000+ government-school children into English sections produced classrooms that teachers described as "neither English nor Tamil" ([Scroll](https://scroll.in/article/839697/lost-for-words-why-tamil-nadus-shift-to-english-medium-instruction-is-not-helping-children)) **[Anecdotal]**.

The important nuance: for *low-income* children, English medium tends to produce weak English *and* weak mother-tongue literacy. For *elite* children with English at home and in the media, it produces strong English and a mother tongue confined to the kitchen. The designer's colleagues are the second group.

---

## 3. Direct evidence on mother-tongue proficiency among English-educated urban Indians

### 3.1 Evidence that supports the observation

**The IIT Bombay heritage-language survey (Sarma 2025, *Frontiers in Human Neuroscience*; n = 1,375 first-year students, 1,098 male / 277 female) [Mixed — self-report, elite sample, but the best Indian data on exactly this question]:**
- >90% had studied school subjects in English; English was acquired at ~6.8 years, with reading fluency (10.3 y) arriving *before* speaking fluency (11.5 y) — the reverse of a home language.
- **Two-thirds prefer to read in English** over any other language; self-rated English reading fluency clusters at 9–10/10 while regional-language reading is "diverse"; regional-language *speaking* is bimodal ("peaks at both ends of the scale… no middle ground").
- **39% report having forgotten a language.** Sanskrit is the most forgotten (12.6%); regional L1s make up ~10% of forgotten languages. Crucially, those who said they "forgot Hindi" typically meant *formal* Hindi, not the Hindi they speak at home.
- Language with parents and elders: Hindi 53%, regional language 47%, **English 0%**. Social media: English ~1,200 responses vs Hindi ~700.
- Authors' conclusion: "the academic or technical domains have been ceded to English/Hindi, and any literacy skills, if gained, are either eroded or lost"; the pattern is "restrictions on the domains of its use" rather than loss ([full text](https://www.frontiersin.org/journals/human-neuroscience/articles/10.3389/fnhum.2025.1538482/full)).

**Marathi in Pune (*Critical Asian Studies* 51(4), 2019) [Mixed]:** middle-class speakers describe Marathi becoming a "kitchen language… increasingly irrelevant for professional use"; of ~200 Grade-7 entrants to a Marathi Olympiad in 2016, *one* came from a Marathi-medium school; even the Marathi-promotion NGO's office ran in English/Hindi; but the author's verdict is *domain shift, not death* ([open-access copy](https://digitalcommons.unf.edu/cgi/viewcontent.cgi?article=4417&context=unf_faculty_publications)).

**Register gap built into the curriculum [Strong for children]:** the LiRIL study in Karnataka found ~80% of words on Grade-1 activity cards were unlikely to be in children's spoken vocabulary — the "school" variety of the regional language is itself a partly foreign register ([IDR](https://idronline.org/why-indian-children-cant-read/)). ASER 2024: only 27.1% of Std-3 and 48.8% of Std-5 children can read a Std-2 text in their regional language ([Ideas for India](https://www.ideasforindia.in/topics/human-development/aser-2024-more-than-a-post-pandemic-recovery-in-learning)).

**Diaspora replica [Mixed]:** among Malayali families in Oman, second-generation children keep spoken Malayalam (with heavy code-switching) while *writing* proficiency declines despite Malayalam classes ([ERIC EJ1287670](https://files.eric.ed.gov/fulltext/EJ1287670.pdf)).

**Journalism/expert testimony [Anecdotal]:** historian A.R. Venkatachalapathy on Tamil university students who "lacked the capacity to read even a newspaper" ([Scroll](https://scroll.in/article/839697/lost-for-words-why-tamil-nadus-shift-to-english-medium-instruction-is-not-helping-children)); Karnataka's recurring "save Kannada" appeals ([Deccan Herald](https://www.deccanherald.com/amp/story/india%2Fkarnataka%2Fto-forget-mother-tongue-lose-2369469)).

### 3.2 Evidence that complicates or contradicts it

- **Base rates (§1.4):** for ~90% of Indians the mother tongue is the only strong language. ASER 2023 (rural 14–18, n = 34,745): **75% can read a Std-2 text in their regional language vs 57.3% who can read simple English sentences**, and only 73.5% of the latter can explain what they read ([Careers360 on ASER 2023](https://news.careers360.com/42-children-in-14-18-age-group-in-rural-india-cant-read-easy-sentences-in-english-report/amp)) **[Strong]**.
- **Oral/domestic dominance persists even in the elite:** 0% of IIT Bombay students use English with parents; in an English-medium Mysore school, 90.6% of children socialised in Kannada at school, although 98% of parents taught home literacy in English ([Language in India, 2007](https://www.languageinindia.com/june2007/schoolgoingchildren.pdf)) **[Mixed]**.
- **Regional-language media is booming, not dying [Strong]:** Indian-language internet users 234 m (2016) → projected 536 m (2021) vs English 175 m → 199 m; 9 of 10 new users are Indian-language users; 68% trust local-language content more ([KPMG–Google 2017](https://assets.kpmg.com/content/dam/kpmg/in/pdf/2017/04/Indian-languages-Defining-Indias-Internet.pdf)). IAMAI–Kantar 2024: 886 m active users, **57% prefer to use the internet in Indian languages** ([Business Standard](https://www.business-standard.com/india-news/use-of-indian-languages-key-for-increasing-internet-access-in-india-report-124031000395_1.html); [Deccan Herald](https://www.deccanherald.com/india/internet-users-in-india-set-to-cross-900-mn-in-2025-fuelled-by-surge-in-indic-language-adoption-3358307)). Deloitte: non-Hindi Indian-language share of OTT consumption 30% (2019) → >50% by 2025 ([TechRadar](https://www.techradar.com/news/other-indian-languages-content-to-overtake-hindi-on-ott-platforms-deloitte)).
- **A gap in the literature:** no study was found that objectively tests *vocabulary size* in the mother tongue vs English among English-medium-educated Indian adults. Everything above is self-report, children's data, or ethnography. The designer's app could generate this data (see §7).

---

## 4. Theory: what the observation most plausibly reflects

**Grosjean's complementarity principle [K]:** bilinguals acquire and use their languages for different purposes, in different domains, with different people; hence dominance and vocabulary are domain-specific and a bilingual is "not two monolinguals in one person" (Grosjean 1989; *Bilingual: Life and Reality*, 2010; Grosjean 2016 in *Language Dominance in Bilinguals*, CUP). Prediction: a Telugu–English professional will name kitchen items, relatives, and emotions faster in Telugu and spreadsheets, contracts, and algorithms faster in English — and will *look* Telugu-weak in any test conducted in a work setting.

**Cummins' BICS vs CALP [K]:** conversational fluency (BICS) takes ~2 years; cognitive-academic language proficiency (CALP) 5–7 years and requires schooling in that language (Cummins 1979/1981/2000). An English-medium child develops BICS in the mother tongue at home and CALP only in English; mother-tongue CALP was simply never built. The interdependence hypothesis also predicts that literacy skills transfer across languages once script is cracked — good news for remediation.

**Heritage-language research (Polinsky & Kagan 2007; Montrul 2008, 2016; Benmamoun, Montrul & Polinsky 2013) [K, and as summarised by Sarma 2025]:** heritage speakers typically show native-like phonology and listening comprehension, reduced productive vocabulary concentrated in home/childhood domains, simplified morphology, command of only informal registers, and frequently no literacy; competence ranges "from complete fluency to barely any ability" and is driven by age of shift, quantity/continuity of input, and schooling (Sarma 2025 cites exactly these sources). The bimodal regional-language speaking ratings at IIT Bombay are the textbook heritage-speaker distribution.

**Diglossia and register [K + Indian data]:** Tamil is a canonical diglossic language (formal *centamiḻ* vs colloquial *koṭuntamiḻ*); Telugu had a century-long *grānthika* vs *vyāvahārika* struggle; Hindi spans Sanskritised *shuddh* Hindi, Hindustani, and Hinglish. The High variety is a school-taught code everywhere. Two Indian findings show how this masquerades as incompetence: IIT Bombay students who "forgot Hindi" had lost *formal* Hindi ([Sarma 2025](https://www.frontiersin.org/journals/human-neuroscience/articles/10.3389/fnhum.2025.1538482/full)), and 80% of Grade-1 Kannada workbook words were outside children's spoken vocabulary ([IDR](https://idronline.org/why-indian-children-cant-read/)).

**Adjudicating (a) vs (b) vs (c):**
- **(c) Sampling bias is certain.** The designer samples people in *education and work* — the two domains where English is institutionally mandated (§2). Even a perfectly balanced bilingual would look English-dominant there. And the sample excludes the ~90% without English.
- **(b) Domain/register asymmetry is the main mechanism** for the people he does meet: intact everyday oral fluency (0% English with parents; Kannada in the playground), weak formal register and literacy (two-thirds prefer reading English; "kitchen language"; can't read a newspaper).
- **(a) Genuine overall attrition is real for a subset:** children of English-speaking homes, interstate migrant families (Hyderabad's IT workforce), and anyone whose mother-tongue exposure stopped before adolescence. There is no Indian survey quantifying this subset; the Pune and IITB data suggest it is growing but still a minority.

So the claim is best restated as: *"Among English-medium-educated urban Indians, the mother tongue is a heritage language: strong in listening and everyday speech, weak in reading, writing, formal register, and adult-domain vocabulary — which is precisely the part of the language that shows up at work."*

---

## 5. Attrition science relevant to "keeping languages alive" [K unless noted]

- **Bahrick (1984), *JEP: General* 113(1):** 773 people tested on Spanish learned in school up to 50 years earlier. Steep loss over the first 3–6 years, then a **plateau lasting ~25–30 years ("permastore")** with little further decline until old age. The amount retained was determined by the *level originally reached* (courses taken, grades), not by rehearsal — most subjects had almost none. Recognition vocabulary survived far better than recall. Bahrick et al. (1993, *Psychological Science*) added that **spaced relearning (e.g., 56-day intervals) beats massed practice** for multi-year retention.
- **Schmid's L1-attrition programme (Schmid 2011, *Language Attrition*, CUP; Schmid & Köpke 2017):** adults who emigrate after ~puberty keep their L1 grammar largely intact for decades even with little use; what degrades is *lexical access* — slower retrieval, hesitations, gaps in low-frequency vocabulary. Amount of use is a surprisingly weak predictor in adults; emotional attitude to the language matters more. Before ~9–12, however, a language can be lost almost entirely (Pallier et al. 2003, Korean adoptees in France) — though Bowers, Mattys & Gage (2009, *Psychological Science*) showed adults who heard **Hindi** or Zulu in childhood and "lost" it retained relearnable phonemic knowledge into their 30s.
- **Order of attrition:** productive before receptive; lexicon before grammar; formal/low-frequency vocabulary and literacy before everyday speech; phonology last. This matches the Indian pattern exactly (writing goes first: Oman Malayalis; formal Hindi goes before spoken Hindi: IIT Bombay).
- **What maintains a language:** continued use in emotionally meaningful relationships; *reading*, because literacy is the only way adult-register vocabulary keeps growing; and periodic spaced retrieval. Family-language-policy research (De Houwer; Spolsky) finds consistent home use plus early literacy (by roughly age 8–10) is the strongest protector of intergenerational transmission.
- **Heritage-language instruction that works:** the US National Heritage Language Survey (Carreira & Kagan 2011, n ≈ 1,700) found heritage learners rate listening/speaking highest and reading/writing lowest, and are motivated by family connection and identity; the effective pedagogy (Kagan & Dillon; Polinsky & Kagan 2007) is "macro-based": start from whole authentic texts and speech, teach the script and formal register explicitly, expand vocabulary by domain, and avoid beginner curricula that bore heritage learners into quitting.

---

## 6. Global analogues that mirror India [K; verify figures]

| Place | What happened | Numbers |
|---|---|---|
| **Singapore** | English-medium schooling since 1987 with compulsory "Mother Tongue Language" subject; home language shifted anyway. | English as most-frequent home language **32.3% (2010) → 48.3% (2020)**; Mandarin 35.6% → 29.9%; Chinese dialects 14.3% → 8.7%; Malay 12.2% → 9.2%; Tamil 3.3% → 2.5% (Census 2020). Among Indian residents, English is now the main home language for ~59%. MOE's 2010 MTL Review reported the share of Primary-1 Chinese pupils from English-speaking homes rising from 28% (1991) to 59% (2010). Result: a generation that "has" a mother tongue as a school subject but weak literacy in it — the closest analogue to urban India. |
| **Philippines** | English is the language of higher education, law and business; Filipino national; ~130 regional languages. Mother-tongue-based multilingual education (K–3) was mandated in 2012–13 and **repealed by RA 12027 in October 2024**, reverting to Filipino and English — a live warning that mother-tongue policy can be undone by parental demand for English. | ~64% of Filipinos speak English (~70 m) but only ~37,000 as L1 ([Wikipedia compilation](https://en.wikipedia.org/wiki/List_of_countries_by_English-speaking_population)); SWS surveys have shown self-rated English speaking/thinking ability slipping through the 2000s. |
| **Ireland** | 13 years of compulsory Irish for everyone; near-zero everyday use. | Census 2022: 1.87 m (≈40%) say they can speak Irish; **~72,000 (1.5%) speak it daily outside education**, down from ~74,000 in 2016. Textbook case of school-taught, never-used language with fast post-school attrition. |
| **Wales** | Welsh-medium schooling as the maintenance engine; home transmission weak. | Census 2021: **17.8% (538,000)** Welsh speakers, down from 19.0% (2011) and 20.8% (2001), despite a "Cymraeg 2050" target of 1 million. |
| **Kenya / Nigeria** | Urban elites shift to English (and Sheng / Nigerian Pidgin); children of Nairobi and Lagos professionals often cannot speak grandparents' languages. UNESCO-linked warnings (2006) that Igbo could be moribund within decades were contested but reflect real urban shift. | ~61% of Nigerians speak English (~125 m), 9.7% as L1 ([Wikipedia compilation](https://en.wikipedia.org/wiki/List_of_countries_by_English-speaking_population)). Evidence base is mostly small studies — **[Mixed/Anecdotal]**. |
| **US Hispanic heritage speakers** | The classic three-generation shift. | Pew (2023): roughly **95%+ of immigrant Latinos, ~75% of the second generation, and only about a third of the third-plus generation** can carry on a conversation in Spanish; nearly all second-generation Latinos speak English fluently while "only about half still speak Spanish" ([Wikipedia](https://en.wikipedia.org/wiki/Languages_of_the_United_States)). |
| **Hong Kong** | The counter-example: high-prestige English in law, business and elite EMI schools, yet the mother tongue stays dominant because it monopolises media, government and street. | Census 2021: Cantonese usual language **~88%**, English ~4.6%; ~59% can speak English. Literacy, however, is in Standard Written Chinese, not Cantonese — a diglossia analogous to India's High/Low registers. |

The pattern across cases: **school-only languages (Ireland) are lost fast; home-only languages (Singapore dialects, third-generation Spanish) lose literacy and formal register first and transmission second; languages that keep a full domain ecology (Hong Kong Cantonese, India's regional-language media) persist orally even when the elite works in English.**

---

## 7. Verdict, and what it means for the app

### 7.1 Verdict

**Partially validated.**

*What is supported:* Among urban, English-medium-educated Indians — especially in Telangana/AP, where 74%+ of school and 93% of undergraduate enrolment is in English — command of the mother tongue's formal register, script, and adult/professional vocabulary is typically weaker than in English. Two-thirds of an elite student sample prefer to read in English; 39% report losing a language; the mother tongue is described as a "kitchen language." Heritage-language theory and attrition science predict this profile precisely. **Confidence: high for the pattern; moderate for magnitude, because no objective vocabulary-size study exists.**

*What is not supported:* "In most cases in India." Around 90% of Indians do not speak English; rural teenagers read their regional language far better than English (75% vs 57%); regional-language internet and OTT use is growing faster than English. And even within the elite, everyday spoken fluency survives: nobody speaks English to their parents. The observation conflates *register* with *competence* and generalises from the two domains (education, work) that India has deliberately handed to English.

*Honest caveat on the designer's method:* judging someone's Telugu at the office is like judging someone's English at a wedding in their village — the complementarity principle guarantees they will look weak. To test the claim properly you need matched tasks in both languages across domains.

### 7.2 Implications for an app that wants to "keep languages alive"

1. **Segment users on day one:** *heritage speaker* (can understand and chat, cannot read/write or discuss adult topics) vs *true beginner* (no exposure). A single 5-minute diagnostic — listen to a short news clip, then read a headline aloud, then describe your job in the language — separates them. Heritage speakers churn from beginner courses that teach "the apple is red."
2. **For heritage speakers, target what is actually missing:**
   - **Script literacy and reading fluency** (decoding akshara scripts is learnable in weeks by someone who already knows the sound system; comprehension follows because BICS vocabulary is intact).
   - **Formal/High register** — the diglossic school variety, news Tamil/Telugu, Sanskritised or Persianised vocabulary for Hindi/Urdu — taught explicitly as a *register*, not as "correct" vs "wrong."
   - **Adult-domain vocabulary**: work, money, health, law, news, technology, emotions-in-argument — the domains ceded to English. Build domain packs, not level packs.
   - **Productive skills**: speaking on non-domestic topics and writing; receptive skills are already the strength.
3. **Design for retention, not just acquisition:** Bahrick's permastore says what is learned to a high level survives decades; his 1993 work says spaced retrieval at long intervals (weeks, not days) is what keeps vocabulary alive. Your "several languages at once" ambition is compatible with this — complementarity means each language can own different domains, so rotate languages by domain rather than drilling all languages on the same word list.
4. **Exploit the regional-language content ecosystem:** 57% of Indian internet users prefer Indian languages; regional OTT is >50% of consumption. Same-language subtitling of film songs is an evidence-based literacy intervention in India ([UNESCO LitBase on PlanetRead](https://www.uil.unesco.org/en/litbase/reading-billion-same-language-subtitling-india)). A reading ladder from subtitles to headlines to op-eds is cheaper and more motivating than invented textbook dialogues.
5. **Make relationships the retention engine:** attrition research says emotional use, not drills, keeps a language; features that route practice through parents, grandparents, and regional-language communities will beat streaks.
6. **Measure and publish:** the literature has no objective test of mother-tongue vs English vocabulary among English-educated Indian adults. A picture-naming or lexical-decision test in both languages, run inside the app, would (a) personalise instruction and (b) produce the first real data on the designer's claim.
7. **Remember the other India:** if the app ever serves the 90% who are not English-dominant, their deficit is the mirror image — English CALP — and the mother-tongue material must be pitched at people who read it better than they read English.

---

## Sources (all fetched in this session unless marked [K])

- The India Forum, "What the Census obscures": https://www.theindiaforum.in/article/what-census-obscures
- Deccan Chronicle on Census 2011 language release: https://www.deccanchronicle.com/nation/in-other-news/010718/over-19500-languages-spoken-in-india-as-mother-tongue-census.html
- Shankar IAS summary of Census 2011 language data: https://www.shankariasparliament.com/article/language-data-of-2011-census
- Wikipedia, Multilingualism in India: https://en.wikipedia.org/wiki/Multilingualism_in_India
- Wikipedia, List of countries by English-speaking population: https://en.wikipedia.org/wiki/List_of_countries_by_English-speaking_population
- Imminent/Translated, "India, do you speak English?": https://imminent.translated.com/india-do-you-speak-english
- thehistoryofenglish.com (Census 2011 and Lok Foundation figures): https://www.thehistoryofenglish.com/how-many-people-in-india-speak-english
- IGIDR WP-2020-015, "India's Search for Link Language and Progress towards Bilingualism": http://www.igidr.ac.in/pdf/publication/WP-2020-015.pdf
- PIB, Census 2027 backgrounder (Apr 2026): https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/apr/doc2026425856601.pdf
- ANI, Census 2027 questionnaire notified (Aug 2026): https://aninews.in/news/national/general-news/census-2027-centre-notifies-40-questions-caste-enumeration-to-take-place-1st-time-since-independence20260814143258/
- Pew Research, Religion in India (2021): https://www.pewresearch.org/religion/wp-content/uploads/sites/7/2021/06/PF_06.29.21_India.full_.report.pdf
- Springer, Language Diversity and Economic Consequences in India: https://link.springer.com/chapter/10.1007/978-3-031-88240-1_20
- Zee News on DISE English-medium growth: https://zeenews.india.com/news/india/student-enrolment-in-english-medium-schools-doubles-in-5-years_1803296.html
- The Quint on NSO 2017-18 medium data: https://www.thequint.com/news/education/percentage-of-english-medium-students-rises-between-kg-to-class-10
- The Federal, UDISE 2019-20 southern states: https://thefederal.com/education/preference-of-english-medium-instruction-rises-in-south-india-except-karnataka
- Asianet Newsable, same UDISE data: https://newsable.asianetnews.com/india/english-becomes-most-preferred-medium-of-instruction-in-all-southern-states-except-karnataka-study-ycb-qvpjq7
- ThePrint on AP English-medium policy: https://theprint.in/india/education/no-more-telugu-medium-in-andhra-why-jagan-govt-wants-english-in-all-schools-colleges/680372/
- Deccan Herald, AP order (Nov 2019): https://www.deccanherald.com/india/soon-ap-will-have-no-more-telugu-medium-govt-schools-773901.html
- The Federal, Telangana UG medium (DOST 2023-24): https://thefederal.com/states/south/telangana/telangana-telugu-medium-takes-a-hit-as-students-switch-to-english-at-ug-level
- Telangana Today, UDISE+ 2023-24 private vs government: https://telanganatoday.com/parents-shun-telangana-government-schools-and-colleges-prefer-private-institutions-udise-report
- Ideas for India, ASER 2024: https://www.ideasforindia.in/topics/human-development/aser-2024-more-than-a-post-pandemic-recovery-in-learning
- Careers360, ASER 2023 Beyond Basics: https://news.careers360.com/42-children-in-14-18-age-group-in-rural-india-cant-read-easy-sentences-in-english-report/amp
- Anhaya Foundation, close reading of NEP 2020 language clauses: https://www.anhayafoundation.com/post/what-nep-2020-actually-says-about-language-and-literacy-and-what-it-does-not-say
- Young Lives, Does Medium of Instruction Affect Learning Outcomes?: https://www.younglives.org.uk/publications/does-medium-instruction-affect-learning-outcomes
- Holla, Impact of Mother Tongue Instruction on Academic Performance (ISI ACEGD 2023): https://www.isid.ac.in/~acegd/acegd2023/papers/ChinmayaHolla.pdf
- Vogelzang et al. 2025, TESOL Quarterly (open access): https://kops.uni-konstanz.de/bitstreams/386b570f-40cb-4a1d-89be-62b50cf0c72d/download
- UNICEF India, Children learn best in their mother tongue: https://www.unicef.org/india/stories/children-learn-best-when-theyre-taught-their-mother-tongue
- Scroll, Tamil Nadu's shift to English medium: https://scroll.in/article/839697/lost-for-words-why-tamil-nadus-shift-to-english-medium-instruction-is-not-helping-children
- Sarma 2025, Frontiers in Human Neuroscience, heritage/multilingualism in India (IIT Bombay survey): https://www.frontiersin.org/journals/human-neuroscience/articles/10.3389/fnhum.2025.1538482/full
- Marathi in Pune, Critical Asian Studies 51(4) 2019 (open-access copy): https://digitalcommons.unf.edu/cgi/viewcontent.cgi?article=4417&context=unf_faculty_publications
- ERIC EJ1287670, Malayalam maintenance among Keralites in Oman: https://files.eric.ed.gov/fulltext/EJ1287670.pdf
- Language in India (2007), languages of school-going children in Mysore: https://www.languageinindia.com/june2007/schoolgoingchildren.pdf
- IDR, Why Indian children can't read (LiRIL): https://idronline.org/why-indian-children-cant-read/
- Deccan Herald, Kannada appeal: https://www.deccanherald.com/amp/story/india%2Fkarnataka%2Fto-forget-mother-tongue-lose-2369469
- KPMG–Google, Indian Languages: Defining India's Internet (2017): https://assets.kpmg.com/content/dam/kpmg/in/pdf/2017/04/Indian-languages-Defining-Indias-Internet.pdf
- Business Standard on IAMAI–Kantar 2024: https://www.business-standard.com/india-news/use-of-indian-languages-key-for-increasing-internet-access-in-india-report-124031000395_1.html
- Deccan Herald on IAMAI–Kantar Internet in India 2024: https://www.deccanherald.com/india/internet-users-in-india-set-to-cross-900-mn-in-2025-fuelled-by-surge-in-indic-language-adoption-3358307
- TechRadar on Deloitte OTT language forecast: https://www.techradar.com/news/other-indian-languages-content-to-overtake-hindi-on-ott-platforms-deloitte
- UNESCO LitBase, Same Language Subtitling (PlanetRead): https://www.uil.unesco.org/en/litbase/reading-billion-same-language-subtitling-india
- Wikipedia, Languages of the United States (generational shift): https://en.wikipedia.org/wiki/Languages_of_the_United_States
- [K] Not fetched this session — standard references: Grosjean (1989, *Brain and Language*; 2010 *Bilingual: Life and Reality*; 2016 in Silva-Corvalán & Treffers-Daller eds., CUP); Cummins (1979 *Review of Educational Research*; 2000 *Language, Power and Pedagogy*); Polinsky & Kagan (2007, *Language and Linguistics Compass* 1(5)); Montrul (2008 *Incomplete Acquisition in Bilingualism*; 2016 *The Acquisition of Heritage Languages*, CUP); Benmamoun, Montrul & Polinsky (2013, *Theoretical Linguistics* 39); Bahrick (1984, *JEP: General* 113(1): 1–29); Bahrick, Bahrick, Bahrick & Bahrick (1993, *Psychological Science* 4(5)); Schmid (2011, *Language Attrition*, CUP); Schmid & Köpke (2017, *Linguistic Approaches to Bilingualism*); Pallier et al. (2003, *Cerebral Cortex*); Bowers, Mattys & Gage (2009, *Psychological Science*); Carreira & Kagan (2011, *Foreign Language Annals*); Azam, Chin & Prakash (2013, *Economic Development and Cultural Change* 61(2)); Singapore Department of Statistics, Census of Population 2020 Release 1; Singapore MOE, Mother Tongue Languages Review (2010); Philippines RA 12027 (2024); CSO Ireland, Census 2022 Irish-language results; ONS/Welsh Government, Census 2021 Welsh-language results; Pew Research Center, "Latinos' views of and experiences with the Spanish language" (Sept 2023); Hong Kong Census and Statistics Department, 2021 Population Census.
