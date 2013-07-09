package timesieve;

import java.io.Serializable;

/**
 * Represents a set of TextEvents pairs with certain fixed values for some attributes
 * (e.g. tense, aspect, and class).  For example, one possible pattern is 
 * (((Tense=PAST, Aspect=PROGRESSIVE),(Tense=FUTURE, Aspect=NONE)), SAME_SENTENCE) representing
 * all pairs of TextEvents in which the first is in the past progressive, the second is in
 * the future, and they are both in the same sentence.
 * 
 * This is useful in constructing rules about text events based on their attributes.
 * 
 * @author Bill
 *
 */
public class TextEventPairPattern implements Serializable {
	private static final long serialVersionUID = 7345682438526525636L;

	public enum SentenceRelation {
		SAME_SENTENCE,
		DIFFERENT_SENTENCE
	}
	
	private TextEventPattern p1 = null;
	private TextEventPattern p2 = null;
	private SentenceRelation sentenceRelation = null;
	
	public TextEventPairPattern() {
		this.p1 = new TextEventPattern();
		this.p2 = new TextEventPattern();
	}
	
	public TextEventPairPattern(TextEvent canonicalEvent1, TextEvent canonicalEvent2, boolean keepClass, boolean keepTense, boolean keepAspect, boolean keepSentenceRelation) {
		setFromCanonicalEvent(canonicalEvent1, canonicalEvent2, keepClass, keepTense, keepAspect, keepSentenceRelation);
	}
	
	public void setFromCanonicalEvent(TextEvent canonicalEvent1, TextEvent canonicalEvent2, boolean keepClass, boolean keepTense, boolean keepAspect, boolean keepSentenceRelation) {
		this.p1.setFromCanonicalEvent(canonicalEvent1, keepClass, keepTense, keepAspect);
		this.p2.setFromCanonicalEvent(canonicalEvent2, keepClass, keepTense, keepAspect);
		
		if (!keepSentenceRelation)
			this.sentenceRelation = null;
		if (canonicalEvent1.getSid() == canonicalEvent2.getSid())
			this.sentenceRelation = SentenceRelation.SAME_SENTENCE;
		else
			this.sentenceRelation = SentenceRelation.DIFFERENT_SENTENCE;
	}
	
	public void setFirstPattern(TextEventPattern p1) {
		this.p1 = p1;
	}
	
	public void setSecondPattern(TextEventPattern p2) {
		this.p2 = p2;
	}
	
	public void setSentenceRelation(SentenceRelation sentenceRelation) {
		this.sentenceRelation = sentenceRelation;
	}
	
	public TextEventPattern getFirstPattern() {
		return this.p1;
	}
	
	public TextEventPattern getSecondPattern() {
		return this.p2;
	}
	
	public SentenceRelation getSentenceRelation() {
		return this.sentenceRelation;
	}
	
	public boolean matches(TextEvent event1, TextEvent event2) {
		return this.p1.matches(event1) 
				&& this.p2.matches(event2) 
				&& (this.sentenceRelation == null 
				 ||	(this.sentenceRelation == SentenceRelation.SAME_SENTENCE && event1.getSid() == event2.getSid())
				 || (this.sentenceRelation == SentenceRelation.DIFFERENT_SENTENCE && event1.getSid() != event2.getSid()));
	}
	
	public int hashCode() {
		return this.p1.hashCode() ^ this.p2.hashCode() ^ ((this.sentenceRelation == null) ? 0 :this.sentenceRelation.hashCode());
	}
	
	public boolean equals(Object o) {
		TextEventPairPattern p = (TextEventPairPattern)o;
		return p.p1.equals(this.p1) && p.p2.equals(this.p2) && this.sentenceRelation == p.sentenceRelation;
	}
	
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(this.p1.toString());
		if (this.sentenceRelation != null)
			str.append("\t").append(this.sentenceRelation).append("\t");
		str.append(this.p2.toString());
		
		return str.toString();
	}
}
