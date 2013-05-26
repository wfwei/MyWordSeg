package mine.seg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * 无需字典的中文分词程序
 * <b>
 * 参考Matrix67 blog（http://www.matrix67.com/blog/archives/5044）
 * 
 * TODO 太耗内存，空间复杂度O(1000N)，N是文档长度
 * 
 * @author WangFengwei 
 * @version 0.1
 */
public class Segment {

	public static void main(String[] args) throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get("resource/test.txt"));
		String src = Charset.forName("utf-8").decode(ByteBuffer.wrap(bytes))
				.toString().replaceAll("[\\s，。,.{}《》<>（）()?？‘’“”；：'`;]", "");
		int maxWordLen = 5;
		double entropyLimit=1d, cohesiveLimit = 1d;
		Segment seg = new Segment(src, maxWordLen, entropyLimit, cohesiveLimit);
		seg.segWord();
		seg.showResult();
	}

	private String src; // 初始文本字符串
	private int totLen; // src的长度
	private int maxWordLen; // 单词最大长度
	private double xEntropy = 1; // 词自由度（熵）阈值
	private double xCohesive = 1; // 词内聚性阈值
	private HashMap<String, WordEntity> words; // 词典<词，属性>
	private static final Logger LOG = Logger.getLogger(Segment.class);

	public Segment(String srcStr, int maxWordLen, double xEntropy,
			double xCohesive) {
		this.src = srcStr;
		this.maxWordLen = maxWordLen;
		this.totLen = srcStr.length();
		this.xEntropy = xEntropy;
		this.xCohesive = xCohesive;
		this.words = new HashMap<String, WordEntity>();
	}

	public void segWord() {
		
		for (int wlen = 1; wlen <= maxWordLen; wlen++) {
			// 遍历词长为wlen的所有字串
			for (int i = 0; i <= totLen - wlen; i++) {
				String wd = src.substring(i, i + wlen);
				if (!words.containsKey(wd))
					words.put(wd, new WordEntity(wd));

				WordEntity wordAttr = words.get(wd);
				// 字串出现次数加1
				wordAttr.addCount();
				if (wlen > 1) {
					if (i > 0)
						wordAttr.addPreWord(src.substring(i - 1, i));
					if (i + wlen < totLen)
						wordAttr.addPostWord(src.substring(i + wlen, i + wlen
								+ 1));
				}
			}

			for (String wd : words.keySet()) {
				if (wd.length() != wlen)
					continue;
				WordEntity wordAttr = words.get(wd);
				wordAttr.calcFreq();
				if (wlen > 1) {
					// 计算自由度
					wordAttr.calcEntropy();
					// 计算内聚性
					wordAttr.calcCohesive(words);
				}
			}
		}
	}

	/**
	 * 输出结果
	 */
	public void showResult() {
		List<Map.Entry<String, WordEntity>> list = new LinkedList<Map.Entry<String, WordEntity>>(
				words.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<String, WordEntity>>() {
			public int compare(Map.Entry<String, WordEntity> m1,
					Map.Entry<String, WordEntity> m2) {
				return m2.getValue().compareTo(m1.getValue());
			}
		});

		for (Map.Entry<String, WordEntity> entry : list) {
			if (entry.getValue().getEntropy() > xEntropy
					&& entry.getValue().getCohesive() > xCohesive)
				LOG.info(entry.getValue());
		}
	}

	/**
	 * 字串的实体类，包含了字串值及各种属性
	 */
	class WordEntity implements Comparable<WordEntity> {
		private String word;
		private int count = 0;
		private double freq = -1;
		private double cohesive = -1;
		private double entropy = -1, preEntropy = -1, postEntropy = -1;
		private double weight = -1;

		private HashMap<String, Integer> preWord = new HashMap<String, Integer>();
		private HashMap<String, Integer> postWord = new HashMap<String, Integer>();

		public WordEntity(String word) {
			this.word = word;
		}

		public void addCount() {
			count++;
		}

		/**
		 * 添加前缀字，用来计算自由度
		 */
		public void addPreWord(String prewd) {
			if (preWord.containsKey(prewd))
				preWord.put(prewd, preWord.get(prewd) + 1);
			else
				preWord.put(prewd, 1);
		}

		/**
		 * 添加后缀字，用来计算自由度
		 */
		public void addPostWord(String postwd) {
			if (postWord.containsKey(postwd))
				postWord.put(postwd, postWord.get(postwd) + 1);
			else
				postWord.put(postwd, 1);
		}

		/**
		 * 计算字串的自由度（熵）
		 */
		public void calcEntropy() {
			if (entropy < 0) {
				preEntropy = calcEntropy(preWord);
				postEntropy = calcEntropy(postWord);
				//选取较大的熵
				entropy = preEntropy > postEntropy ? postEntropy : preEntropy;
			}
		}

		/**
		 * 计算字串的内聚性
		 * TODO 可以改进，考虑前缀词的自由度
		 */
		public void calcCohesive(HashMap<String, WordEntity> words) {
			double maxSubFreq = -1;
			int len = word.length();

			for (int sublen = 1; sublen < len; sublen++) {
				double prefreq = words.get(this.word.substring(0, sublen))
						.getFreq();
				double postfreq = words.get(this.word.substring(sublen, len))
						.getFreq();
				double subfreq = prefreq * postfreq;
				if (subfreq > maxSubFreq)
					maxSubFreq = subfreq;
			}

			this.cohesive = freq / maxSubFreq / Math.pow(this.word.length(), 2);
		}

		private double calcEntropy(HashMap<String, Integer> words) {
			double tot = 0d;
			for (Integer count : words.values()) {
				tot += count;
			}
			double entropy = 0;
			for (Integer count : words.values()) {
				double perc = count / tot;
				entropy += -perc * Math.log(perc);
			}
			return entropy;
		}

		public int getCount() {
			return count;
		}

		public double getFreq() {
			return freq;
		}

		public void calcFreq() {
			freq = ((double) count) / totLen;
		}

		public double getEntropy() {
			return entropy;
		}

		public double getCohesive() {
			return cohesive;
		}

		public double getWeight() {
			if (weight < 0) {
				weight = cohesive * entropy * entropy;
			}
			return weight;
		}

		@Override
		public int compareTo(WordEntity attr) {
			if (this.getWeight() > attr.getWeight())
				return 1;
			else if (this.getWeight() < attr.getWeight())
				return -1;
			return 0;
		}

		@Override
		public String toString() {
			return String
					.format("%s\t{count:%d, freq:%.5f, cohesive:%.5f, entropy:%.5f}",
							word, count, freq, cohesive, entropy);
		}
	}

}
