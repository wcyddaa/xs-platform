package com.xingshu.book.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PdfParsingService {

    /** 每块最小字符数（如果累计不足此值，继续合并后续段落） */
    private static final int CHUNK_MIN_CHARS = 200;
    /** 每块最大字符数 */
    private static final int CHUNK_MAX_CHARS = 1500;
    /**
     * 段落分隔正则（参考 LangChain RecursiveCharacterTextSplitter 层级分隔策略）：
     * 1. 空行（\n\n 或 \n\s*\n）—— 最可靠的段落边界
     * 2. 换行后跟缩进（2+空格）+ 汉字 —— 中文段落起始标志
     * 
     * 注意：不会匹配段内换行（如"这是第一句。\n这是第二句。"），
     * 因为段内换行后没有缩进。
     */
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile(
            "\\n\\s*\\n|\\n(?= {2,}[\\u4e00-\\u9fa5])"
    );
    /** 句子结束符（用于PPT等无段落结构的文本兜底分隔） */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；])(?=\\S)");

    /**
     * 解析 PDF 文件，提取全本文本
     */
    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 解析 PDF 并返回按页的文本列表
     */
    public List<PageText> extractPages(MultipartFile file) throws IOException {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            int totalPages = document.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                if (!text.isBlank()) {
                    pages.add(new PageText(i, text));
                }
            }
        }
        return pages;
    }

    /**
     * 将 PDF 按页+段落切块，确保段落完整不跨页分割。
     * 
     * 策略：
     * 1. 先按页提取文本，每页内按段落分割
     * 2. 如果某页的最后一个段落不完整（没有句号/问号/感叹号结尾），
     *    则与下一页的第一个段落合并
     * 3. 相邻的短段落合并为一个 chunk（避免 PPT 等文本产生过多碎片）
     * 4. 每个 chunk 记录起始页、结束页、段落索引，方便模型回答"教材第几页第几段"
     */
    public List<ChunkResult> chunkByPageAndParagraph(MultipartFile file) throws IOException {
        List<ChunkResult> chunks = new ArrayList<>();
        List<PageText> pages = extractPages(file);
        if (pages.isEmpty()) return chunks;

        // 第一步：每页按段落拆分
        List<List<String>> pageParagraphs = new ArrayList<>();
        for (PageText page : pages) {
            List<String> paragraphs = splitParagraphs(page.text());
            // 过滤空段落
            paragraphs = paragraphs.stream()
                    .filter(p -> !p.isBlank())
                    .map(String::trim)
                    .toList();
            pageParagraphs.add(paragraphs);
        }

        // 第二步：合并跨页段落 + 合并短段落，生成 chunk
        int chunkIndex = 0;
        String pendingText = null;  // 上一页未完成的段落
        int pendingStartPage = 0;
        // 累积缓冲区：用于合并同页的短段落
        StringBuilder accBuffer = new StringBuilder();
        int accStartPage = 0;
        int accParaIndex = 0;

        for (int i = 0; i < pageParagraphs.size(); i++) {
            List<String> paragraphs = pageParagraphs.get(i);
            int currentPage = pages.get(i).pageNumber();
            if (paragraphs.isEmpty()) continue;

            int startIdx = 0;
            int paraIndexOnPage = 1;

            // 如果有上一页未完成的段落，与当前页第一段合并
            if (pendingText != null) {
                String merged = pendingText + paragraphs.get(0);
                // 合并后放入累积缓冲区
                flushAccumulated(chunks, accBuffer, accStartPage, currentPage, accParaIndex, chunkIndex);
                accBuffer = new StringBuilder(merged);
                accStartPage = pendingStartPage;
                accParaIndex = paraIndexOnPage;
                chunkIndex = chunks.size();
                pendingText = null;
                startIdx = 1;
                paraIndexOnPage++;
            }

            // 处理当前页的段落
            for (int j = startIdx; j < paragraphs.size(); j++) {
                String para = paragraphs.get(j);

                // 检查最后一段是否不完整（跨页）
                if (j == paragraphs.size() - 1 && i < pageParagraphs.size() - 1) {
                    if (!isParagraphComplete(para)) {
                        // 先刷新累积缓冲区
                        flushAccumulated(chunks, accBuffer, accStartPage, currentPage, accParaIndex, chunkIndex);
                        chunkIndex = chunks.size();
                        pendingText = para;
                        pendingStartPage = currentPage;
                        accBuffer = new StringBuilder();
                        accStartPage = 0;
                        accParaIndex = 0;
                        continue;
                    }
                }

                // 累积短段落，合并到 ChunkMinChars
                if (accBuffer.length() == 0) {
                    accBuffer.append(para);
                    accStartPage = currentPage;
                    accParaIndex = paraIndexOnPage;
                } else {
                    accBuffer.append("\n\n").append(para);
                }
                paraIndexOnPage++;

                // 如果累积够了，刷新为一个 chunk
                if (accBuffer.length() >= CHUNK_MIN_CHARS) {
                    flushAccumulated(chunks, accBuffer, accStartPage, currentPage, accParaIndex, chunkIndex);
                    chunkIndex = chunks.size();
                    accBuffer = new StringBuilder();
                    accStartPage = 0;
                    accParaIndex = 0;
                }
            }

            // 页末：如果累积缓冲区还有内容且非跨页，刷新
            if (accBuffer.length() > 0 && pendingText == null) {
                flushAccumulated(chunks, accBuffer, accStartPage, currentPage, accParaIndex, chunkIndex);
                chunkIndex = chunks.size();
                accBuffer = new StringBuilder();
                accStartPage = 0;
                accParaIndex = 0;
            }
        }

        // 处理最后剩余的未完成段落
        if (pendingText != null) {
            if (accBuffer.length() > 0) {
                accBuffer.append("\n\n").append(pendingText);
            } else {
                accBuffer.append(pendingText);
                accStartPage = pendingStartPage;
            }
            flushAccumulated(chunks, accBuffer, accStartPage,
                    pages.get(pages.size() - 1).pageNumber(), accParaIndex > 0 ? accParaIndex : 1, chunkIndex);
        } else if (accBuffer.length() > 0) {
            flushAccumulated(chunks, accBuffer, accStartPage,
                    pages.get(pages.size() - 1).pageNumber(), accParaIndex > 0 ? accParaIndex : 1, chunkIndex);
        }

        return chunks;
    }

    /**
     * 刷新累积缓冲区为一个 chunk，如果文本过长则按字符数拆分
     */
    private void flushAccumulated(List<ChunkResult> chunks, StringBuilder buffer,
                                   int startPage, int endPage, int paraIndex, int baseChunkIndex) {
        if (buffer.length() == 0) return;
        String text = buffer.toString().trim();
        if (text.isBlank()) return;

        if (text.length() <= CHUNK_MAX_CHARS) {
            chunks.add(new ChunkResult(startPage, endPage, text, 1, paraIndex));
        } else {
            // 文本过长，按字符数拆分，尽量在句号处断开
            int start = 0;
            int partIndex = 1;
            while (start < text.length()) {
                int end = Math.min(start + CHUNK_MAX_CHARS, text.length());
                if (end < text.length()) {
                    int lastPeriod = text.lastIndexOf("。", end);
                    if (lastPeriod > start + CHUNK_MAX_CHARS / 2) {
                        end = lastPeriod + 1;
                    }
                }
                chunks.add(new ChunkResult(startPage, endPage,
                        text.substring(start, end).trim(), partIndex, paraIndex));
                start = end;
                partIndex++;
            }
        }
    }

    /**
     * 按段落分隔符拆分文本（层级分隔策略）
     * 
     * 参考 LangChain RecursiveCharacterTextSplitter：
     * 1. 优先用空行分隔 —— 这是最可靠的段落边界
     * 2. 其次用缩进+汉字开头分隔 —— 中文段落起始标志
     * 3. 兜底用句子级分隔（。！？） —— 避免盲目合并不相关内容
     * 4. 最终兜底：整段作为一个段落
     */
    private List<String> splitParagraphs(String text) {
        // 层级1：用空行分隔
        String[] byEmptyLine = text.split("\\n\\s*\\n");
        if (byEmptyLine.length > 1) {
            return Arrays.stream(byEmptyLine)
                    .map(String::trim)
                    .filter(p -> !p.isBlank())
                    .toList();
        }

        // 层级2：用缩进段落起始分隔
        String[] parts = PARAGRAPH_SPLIT.split(text);
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.size() > 1) {
            return paragraphs;
        }

        // 层级3：没有段落结构（PPT等），按句子分隔
        String[] sentences = SENTENCE_END.split(text);
        List<String> sentenceList = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s.trim();
            if (!trimmed.isBlank()) {
                sentenceList.add(trimmed);
            }
        }
        if (sentenceList.size() > 1) {
            return sentenceList;
        }

        // 层级4：最终兜底 —— 整段作为一个段落
        String trimmed = text.trim();
        return trimmed.isBlank() ? List.of() : List.of(trimmed);
    }

    /**
     * 判断段落是否完整（以句号、问号、感叹号、分号、省略号结尾）
     */
    private boolean isParagraphComplete(String text) {
        if (text == null || text.isBlank()) return true;
        char lastChar = text.charAt(text.length() - 1);
        return lastChar == '。' || lastChar == '？' || lastChar == '！'
                || lastChar == '；' || lastChar == '…' || lastChar == '.'
                || lastChar == '?' || lastChar == '!';
    }

    /**
     * 获取 PDF 总页数
     */
    public int getPageCount(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return document.getNumberOfPages();
        }
    }

    /**
     * 将 PDF 的指定页渲染为 PNG 图片（高清，200 DPI）
     * 用于前端图片流预览，杜绝直接下载PDF文件
     *
     * @param pdfBytes PDF 文件字节数组
     * @param pageNum  页码（从 1 开始）
     * @param dpi      渲染分辨率（默认 200）
     * @return PNG 图片字节数组
     */
    public byte[] renderPageAsImage(byte[] pdfBytes, int pageNum, int dpi) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            if (pageNum < 1 || pageNum > totalPages) {
                throw new IllegalArgumentException("页码超出范围: " + pageNum + "，总页数: " + totalPages);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageNum - 1, dpi);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    /**
     * 抽取 PDF 的指定页范围，生成一个新的 PDF（用于由教材生成课件）
     *
     * @param pdfBytes  源 PDF 字节数组
     * @param startPage 起始页（从 1 开始，含）
     * @param endPage   结束页（从 1 开始，含）
     * @return 新 PDF 字节数组
     */
    public byte[] extractPageRange(byte[] pdfBytes, int startPage, int endPage) throws IOException {
        try (PDDocument source = Loader.loadPDF(pdfBytes);
             PDDocument target = new PDDocument()) {
            int totalPages = source.getNumberOfPages();
            if (startPage < 1 || endPage < startPage || endPage > totalPages) {
                throw new IllegalArgumentException(
                        "页码超出范围: " + startPage + "-" + endPage + "，总页数: " + totalPages);
            }
            // PDFBox 页面索引从 0 开始
            for (int pageIndex = startPage - 1; pageIndex < endPage; pageIndex++) {
                target.importPage(source.getPage(pageIndex));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            target.save(out);
            return out.toByteArray();
        }
    }

    /**
     * 获取 PDF 文件名（不含扩展名，作为默认标题）
     */
    public static String extractTitle(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null) return "未命名教材";
        int dotIndex = originalName.lastIndexOf('.');
        return dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
    }

    // ========== 内部记录类 ==========

    public record PageText(int pageNumber, String text) {}

    public record ChunkResult(int startPage, int endPage, String content, int partIndex, int paragraphIndex) {}

    /**
     * 课件用：逐页提取文本（每页直接返回原始文本，不做段落拆分）
     */
    public record PageContent(int pageNumber, String content) {}

    public List<PageContent> extractPageContents(MultipartFile file) throws IOException {
        List<PageContent> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            int totalPages = document.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                pages.add(new PageContent(i, text != null ? text.trim() : ""));
            }
        }
        return pages;
    }
}