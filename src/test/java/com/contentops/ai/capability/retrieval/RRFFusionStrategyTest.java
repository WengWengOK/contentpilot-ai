package com.contentops.ai.capability.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link RRFFusionStrategy} 单元测试。
 * <p>
 * 纯单元测试, 不依赖 Spring 上下文, 直接 new 出被测对象。
 */
@DisplayName("RRFFusionStrategy RRF 融合策略测试")
class RRFFusionStrategyTest {

    private RRFFusionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RRFFusionStrategy();
    }

    private Document doc(String id, String content, Document.Source source) {
        return Document.builder()
                .id(id)
                .content(content)
                .score(0.0)
                .source(source)
                .build();
    }

    @Nested
    @DisplayName("两路结果融合：向量检索 + BM25 检索")
    class FuseTwoWays {

        @Test
        @DisplayName("向量与 BM25 完全不相交时, 融合结果包含全部文档")
        void should_fuseDisjointResultsFromBothWays() {
            List<Document> vectorResults = Arrays.asList(
                    doc("v1", "向量结果1", Document.Source.VECTOR),
                    doc("v2", "向量结果2", Document.Source.VECTOR));
            List<Document> bm25Results = Arrays.asList(
                    doc("b1", "BM25结果1", Document.Source.BM25),
                    doc("b2", "BM25结果2", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results);

            assertThat(fused).hasSize(4);
            assertThat(fused).extracting(Document::getId)
                    .containsExactlyInAnyOrder("v1", "v2", "b1", "b2");
            assertThat(fused).allSatisfy(d ->
                    assertThat(d.getSource()).isEqualTo(Document.Source.FUSED));
        }

        @Test
        @DisplayName("融合后所有文档 source 标记为 FUSED 且分数被覆写为融合分数")
        void should_markFusedSourceAndOverwriteScore() {
            List<Document> vectorResults = Collections.singletonList(
                    doc("v1", "内容", Document.Source.VECTOR));

            List<Document> fused = strategy.fuse(vectorResults, Collections.emptyList());

            assertThat(fused).hasSize(1);
            Document fusedDoc = fused.get(0);
            assertThat(fusedDoc.getSource()).isEqualTo(Document.Source.FUSED);
            // 原始文档内容应被保留(toBuilder)
            assertThat(fusedDoc.getContent()).isEqualTo("内容");
            // rank=1, k=60 -> 1/(60+1) = 1/61
            assertThat(fusedDoc.getScore()).isCloseTo(1.0 / 61, within(1e-9));
        }

        @Test
        @DisplayName("仅向量有结果时也能正常融合")
        void should_fuseWhenOnlyVectorHasResults() {
            List<Document> vectorResults = Arrays.asList(
                    doc("v1", "向量1", Document.Source.VECTOR),
                    doc("v2", "向量2", Document.Source.VECTOR));

            List<Document> fused = strategy.fuse(vectorResults, Collections.emptyList());

            assertThat(fused).hasSize(2);
            assertThat(fused).extracting(Document::getId).containsExactly("v1", "v2");
        }
    }

    @Nested
    @DisplayName("文档在两路中都出现时分数累加")
    class AccumulateScore {

        @Test
        @DisplayName("同一文档在两路均排第一时, 融合分数为两路贡献之和")
        void should_accumulateScoreWhenDocAppearsInBothWays() {
            List<Document> vectorResults = Collections.singletonList(
                    doc("d1", "共享文档", Document.Source.VECTOR));
            List<Document> bm25Results = Collections.singletonList(
                    doc("d1", "共享文档", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results);

            assertThat(fused).hasSize(1);
            // 两路均 rank=1, k=60 -> 1/61 + 1/61 = 2/61
            assertThat(fused.get(0).getScore()).isCloseTo(2.0 / 61, within(1e-9));
        }

        @Test
        @DisplayName("两路出现同一文档时, 只保留一份(按 ID 去重)")
        void should_deduplicateDocAppearingInBothWays() {
            List<Document> vectorResults = Arrays.asList(
                    doc("d1", "共享", Document.Source.VECTOR),
                    doc("d2", "仅向量", Document.Source.VECTOR));
            List<Document> bm25Results = Arrays.asList(
                    doc("d1", "共享", Document.Source.BM25),
                    doc("d3", "仅BM25", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results);

            assertThat(fused).hasSize(3);
            assertThat(fused).extracting(Document::getId)
                    .containsExactlyInAnyOrder("d1", "d2", "d3");
        }

        @Test
        @DisplayName("累加后的分数大于单路贡献")
        void should_accumulatedScoreBeGreaterThanSingleContribution() {
            List<Document> vectorResults = Collections.singletonList(
                    doc("d1", "共享", Document.Source.VECTOR));
            List<Document> bm25Results = Collections.singletonList(
                    doc("d1", "共享", Document.Source.BM25));

            List<Document> fusedBoth = strategy.fuse(vectorResults, bm25Results);
            List<Document> fusedVectorOnly = strategy.fuse(vectorResults, Collections.emptyList());

            assertThat(fusedBoth.get(0).getScore())
                    .isGreaterThan(fusedVectorOnly.get(0).getScore());
        }
    }

    @Nested
    @DisplayName("空列表与 null 输入")
    class EmptyAndNullInput {

        @Test
        @DisplayName("两路均为空列表时返回空结果")
        void should_returnEmptyWhenBothListsEmpty() {
            List<Document> fused = strategy.fuse(Collections.emptyList(), Collections.emptyList());

            assertThat(fused).isEmpty();
        }

        @Test
        @DisplayName("向量结果为 null 时仅融合 BM25 结果")
        void should_handleNullVectorResults() {
            List<Document> bm25Results = Arrays.asList(
                    doc("b1", "BM25结果1", Document.Source.BM25),
                    doc("b2", "BM25结果2", Document.Source.BM25));

            List<Document> fused = strategy.fuse(null, bm25Results);

            assertThat(fused).hasSize(2);
            assertThat(fused).extracting(Document::getId).containsExactly("b1", "b2");
        }

        @Test
        @DisplayName("BM25 结果为 null 时仅融合向量结果")
        void should_handleNullBm25Results() {
            List<Document> vectorResults = Arrays.asList(
                    doc("v1", "向量结果1", Document.Source.VECTOR),
                    doc("v2", "向量结果2", Document.Source.VECTOR));

            List<Document> fused = strategy.fuse(vectorResults, null);

            assertThat(fused).hasSize(2);
            assertThat(fused).extracting(Document::getId).containsExactly("v1", "v2");
        }

        @Test
        @DisplayName("两路均为 null 时返回空结果, 不抛出异常")
        void should_returnEmptyWhenBothNull() {
            List<Document> fused = strategy.fuse(null, null);

            assertThat(fused).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("一边为空列表另一边为 null 时返回空结果")
        void should_returnEmptyWhenOneEmptyOneNull() {
            List<Document> fused = strategy.fuse(Collections.emptyList(), null);

            assertThat(fused).isEmpty();
        }
    }

    @Nested
    @DisplayName("自定义 k 参数")
    class CustomK {

        @Test
        @DisplayName("自定义 k=1 时按 1/(1+rank) 计算融合分数")
        void should_useCustomKParameter() {
            List<Document> vectorResults = Collections.singletonList(
                    doc("d1", "文档", Document.Source.VECTOR));
            List<Document> bm25Results = Collections.singletonList(
                    doc("d1", "文档", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results, 1);

            // 两路均 rank=1, k=1 -> 1/(1+1) + 1/(1+1) = 1.0
            assertThat(fused).hasSize(1);
            assertThat(fused.get(0).getScore()).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("较小的 k 会放大排名靠前文档的分数差距")
        void should_smallerKAmplifyRankDifference() {
            List<Document> vectorResults = Arrays.asList(
                    doc("d1", "文档1", Document.Source.VECTOR),
                    doc("d2", "文档2", Document.Source.VECTOR));

            List<Document> fusedK1 = strategy.fuse(vectorResults, Collections.emptyList(), 1);
            List<Document> fusedK60 = strategy.fuse(vectorResults, Collections.emptyList(), 60);

            double diffK1 = fusedK1.get(0).getScore() - fusedK1.get(1).getScore();
            double diffK60 = fusedK60.get(0).getScore() - fusedK60.get(1).getScore();
            // k=1 时 rank1 与 rank2 的分数差更大
            assertThat(diffK1).isGreaterThan(diffK60);
        }

        @Test
        @DisplayName("k<=0 时回退到默认 DEFAULT_K(60)")
        void should_fallbackToDefaultKWhenNonPositive() {
            List<Document> vectorResults = Collections.singletonList(
                    doc("d1", "文档", Document.Source.VECTOR));

            List<Document> fusedWithZero = strategy.fuse(vectorResults, Collections.emptyList(), 0);
            List<Document> fusedWithNegative = strategy.fuse(vectorResults, Collections.emptyList(), -10);
            List<Document> fusedWithDefault = strategy.fuse(vectorResults, Collections.emptyList(), RRFFusionStrategy.DEFAULT_K);

            // k<=0 与默认 k=60 结果一致
            assertThat(fusedWithZero.get(0).getScore())
                    .isCloseTo(fusedWithDefault.get(0).getScore(), within(1e-9));
            assertThat(fusedWithNegative.get(0).getScore())
                    .isCloseTo(fusedWithDefault.get(0).getScore(), within(1e-9));
        }

        @Test
        @DisplayName("无参 fuse 方法使用默认 k=60")
        void should_useDefaultKWhenNoKProvided() {
            List<Document> vectorResults = Collections.singletonList(
                    doc("d1", "文档", Document.Source.VECTOR));

            List<Document> fusedNoArg = strategy.fuse(vectorResults, Collections.emptyList());
            List<Document> fusedDefaultK = strategy.fuse(vectorResults, Collections.emptyList(), RRFFusionStrategy.DEFAULT_K);

            assertThat(fusedNoArg.get(0).getScore())
                    .isCloseTo(fusedDefaultK.get(0).getScore(), within(1e-9));
        }

        @Test
        @DisplayName("DEFAULT_K 常量值为 60")
        void should_defaultKConstantBe60() {
            assertThat(RRFFusionStrategy.DEFAULT_K).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("排序正确性(按融合分数降序)")
    class Sorting {

        @Test
        @DisplayName("融合结果按融合分数降序排列")
        void should_sortByFusedScoreDescending() {
            // d1 仅向量 rank1 -> 1/61
            // d2 向量 rank2 + BM25 rank1 -> 1/62 + 1/61 (最高)
            // d3 仅 BM25 rank2 -> 1/62
            List<Document> vectorResults = Arrays.asList(
                    doc("d1", "仅向量rank1", Document.Source.VECTOR),
                    doc("d2", "两路共享", Document.Source.VECTOR));
            List<Document> bm25Results = Arrays.asList(
                    doc("d2", "两路共享", Document.Source.BM25),
                    doc("d3", "仅BM25rank2", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results);

            // 期望顺序: d2(最高) > d1 > d3
            assertThat(fused).extracting(Document::getId)
                    .containsExactly("d2", "d1", "d3");
            // 验证分数严格递减
            assertThat(fused.get(0).getScore()).isGreaterThan(fused.get(1).getScore());
            assertThat(fused.get(1).getScore()).isGreaterThan(fused.get(2).getScore());
        }

        @Test
        @DisplayName("出现于两路的文档即使单路排名靠后, 也能排在仅单路靠前的文档之前")
        void should_multiListDocRankHigherThanSingleListDoc() {
            // d2 在向量中排第2, 但同时在 BM25 中排第1 -> 融合分数最高
            List<Document> vectorResults = Arrays.asList(
                    doc("d1", "仅向量rank1", Document.Source.VECTOR),
                    doc("d2", "共享", Document.Source.VECTOR));
            List<Document> bm25Results = Collections.singletonList(
                    doc("d2", "共享", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results);

            assertThat(fused.get(0).getId()).isEqualTo("d2");
            assertThat(fused.get(0).getScore()).isGreaterThan(fused.get(1).getScore());
        }

        @Test
        @DisplayName("仅单路结果时按原始排名降序排列")
        void should_sortSingleListByRankDescending() {
            List<Document> vectorResults = Arrays.asList(
                    doc("a", "A", Document.Source.VECTOR),
                    doc("b", "B", Document.Source.VECTOR),
                    doc("c", "C", Document.Source.VECTOR));

            List<Document> fused = strategy.fuse(vectorResults, Collections.emptyList());

            assertThat(fused).extracting(Document::getId).containsExactly("a", "b", "c");
            assertThat(fused.get(0).getScore()).isGreaterThan(fused.get(1).getScore());
            assertThat(fused.get(1).getScore()).isGreaterThan(fused.get(2).getScore());
        }
    }

    @Nested
    @DisplayName("文档 ID 为 null 时跳过")
    class NullIdSkip {

        @Test
        @DisplayName("ID 为 null 的文档被跳过, 不计入融合结果")
        void should_skipDocsWithNullId() {
            List<Document> vectorResults = Arrays.asList(
                    doc("v1", "有效", Document.Source.VECTOR),
                    doc(null, "无ID", Document.Source.VECTOR));
            List<Document> bm25Results = Arrays.asList(
                    doc(null, "无ID", Document.Source.BM25),
                    doc("b1", "有效BM25", Document.Source.BM25));

            List<Document> fused = strategy.fuse(vectorResults, bm25Results);

            assertThat(fused).hasSize(2);
            assertThat(fused).extracting(Document::getId)
                    .containsExactlyInAnyOrder("v1", "b1");
        }

        @Test
        @DisplayName("所有文档 ID 均为 null 时返回空结果")
        void should_returnEmptyWhenAllIdsNull() {
            List<Document> vectorResults = Arrays.asList(
                    doc(null, "无ID1", Document.Source.VECTOR),
                    doc(null, "无ID2", Document.Source.VECTOR));

            List<Document> fused = strategy.fuse(vectorResults, Collections.emptyList());

            assertThat(fused).isEmpty();
        }

        @Test
        @DisplayName("ID 为 null 的文档不影响其他文档的排名计算")
        void should_nullIdDocNotAffectOtherRanks() {
            // null ID 文档位于 index0, 有效文档位于 index1(rank=2)
            List<Document> vectorResults = Arrays.asList(
                    doc(null, "无ID", Document.Source.VECTOR),
                    doc("v1", "有效", Document.Source.VECTOR));

            List<Document> fused = strategy.fuse(vectorResults, Collections.emptyList());

            assertThat(fused).hasSize(1);
            // rank 仍按原始位置 index1 -> rank=2 -> 1/(60+2) = 1/62
            assertThat(fused.get(0).getScore()).isCloseTo(1.0 / 62, within(1e-9));
        }
    }
}
