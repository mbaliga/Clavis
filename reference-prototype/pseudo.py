"""Build pseudowords for the yes/no tests (Latin-script lanes) from the corpus lexicon."""
import json, random, wordfreq
lex = json.load(open('lexicon.json')); random.seed(7)
def pseudo(lang, n=10):
    words = [w for w,(c,z,s) in lex[lang].items() if len(w)>=5 and z>0 and w.isalpha()]
    out=set(); tries=0
    while len(out)<n and tries<5000:
        tries+=1; a,b = random.sample(words,2)
        cand = a[:random.randint(2,len(a)-2)] + b[random.randint(2,len(b)-2):]
        if 5<=len(cand)<=10 and cand not in lex[lang] and wordfreq.zipf_frequency(cand,lang)==0 and wordfreq.zipf_frequency(cand,'en')==0:
            out.add(cand)
    return sorted(out)
json.dump({l:pseudo(l) for l in ['es','de','fr','it','nl']}, open('pseudo.json','w'))
