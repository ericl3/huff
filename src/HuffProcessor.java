import java.util.*;
/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root, "", new String[257]);
		writeHeader(root, out);
		
		in.reset();
		
		writeCompressedBits(in, codings, out);
	   /* while (true){
	        int val = in.readBits(BITS_PER_WORD);
	        if (val == -1) break;
	        
	        out.writeBits(BITS_PER_WORD, val);
	    }*/
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int magicNumber = in.readBits(BITS_PER_INT);
		if (magicNumber != HUFF_TREE) {
			throw new HuffException("Not magic, thus not compressed file!");
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
	    /*while (true){
            int val = in.readBits(BITS_PER_WORD);
            if (val == -1) break;
            out.writeBits(BITS_PER_WORD, val);
        }*/
	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit==0) {
			return new HuffNode(98, 99, readTreeHeader(in), readTreeHeader(in)); // int. node + subtrees
		} else {
			return new HuffNode(in.readBits(BITS_PER_WORD+1), 99); // constructs leaf node 
		}
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		int bits;
		HuffNode current = root;
		while (true) {
			bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) current = current.left();
				else if (bits == 1) current = current.right();
				
				if (current.left() == null && current.right() == null) {
					if (current.value() == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(8, current.value());
						current = root;
					}
				}
			}
		}	
	}
	
	private int[] readForCounts(BitInputStream in) {
		/*Map<Integer, Integer> bitMap = new TreeMap<>();
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) break;
			
			if(!bitMap.containsKey(val)) {
				bitMap.put(val, 1);
			} else {
				bitMap.put(val, bitMap.get(val)+1);
			}
		}
		int[] ret = new int[256];
		for(Integer x : bitMap.keySet()) {
			ret[x] = bitMap.get(x);
		}
		return ret;*/
		int[] ret = new int[256];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) break;
			ret[val] +=1;
		}
		return ret;
	}
	
	private HuffNode makeTreeFromCounts(int[] bitCounts) {
		int counter = 0;
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < bitCounts.length; i++) {
			int count = bitCounts[i];
			if (count>0) {
				counter++;
				pq.add(new HuffNode(i, count));
			}
		}
		pq.add(new HuffNode(PSEUDO_EOF, 1));
		while(pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.weight()+right.weight(), left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		System.out.println(counter);
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode HuffMe, String path, String[] codings) {
		if(HuffMe.left() == null & HuffMe.right() == null) {
			codings[HuffMe.value()] = path;
		}
		if(HuffMe.left()!=null) {
			
			makeCodingsFromTree(HuffMe.left(), path + "0", codings);
		}
		if(HuffMe.right()!=null) {
			makeCodingsFromTree(HuffMe.right(), path + "1", codings);
		}
		return codings;
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out) {
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out);
	}
	
	private void writeTree(HuffNode HuffMe, BitOutputStream out) {
		if(HuffMe.left() == null && HuffMe.right() == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, HuffMe.value());
			return;
		}
		
		out.writeBits(1, 0);
		if (HuffMe.left()!=null) {
			writeTree(HuffMe.left(), out);
		}
		if(HuffMe.right()!=null) {
			writeTree(HuffMe.right(), out);
		}
	}
	
	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out) {
	while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val==-1) {
				out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF],2));
				break;
			}
			String encode = codings[val];
			out.writeBits(encode.length(), Integer.parseInt(encode, 2));
	}
}
}