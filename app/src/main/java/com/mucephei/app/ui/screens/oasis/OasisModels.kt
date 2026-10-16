package com.mucephei.app.ui.screens.oasis

import java.util.UUID

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BookmarkItem(
    val url: String,
    val title: String
)

data class BrowserDownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val mimeType: String,
    var progress: Int = 0,
    var isComplete: Boolean = false,
    val destinationPath: String
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "",
    var title: String = "Speed Dial",
    var scrollY: Int = 0
)

data class SpeedDialItem(
    val title: String,
    val url: String,
    val subtitle: String = "",
    val faviconUrl: String? = null
)

val academicSpeedDialEntries = listOf(
    SpeedDialItem("Google Scholar", "https://scholar.google.com", "Academic search engine"),
    SpeedDialItem("arXiv", "https://arxiv.org", "Open-access preprints in physics, math, computer science"),
    SpeedDialItem("ResearchGate", "https://www.researchgate.net", "Professional network for scientists and researchers"),
    SpeedDialItem("JSTOR", "https://www.jstor.org", "Digital library of academic journals and books"),
    SpeedDialItem("PubMed", "https://pubmed.ncbi.nlm.nih.gov", "Biomedical and life sciences literature"),
    SpeedDialItem("IEEE Xplore", "https://ieeexplore.ieee.org", "Engineering and technology literature"),
    SpeedDialItem("ScienceDirect", "https://www.sciencedirect.com", "Peer-reviewed scientific, technical and medical research"),
    SpeedDialItem("Semantic Scholar", "https://www.semanticscholar.org", "AI-powered research tool for scientific literature"),
    SpeedDialItem("Wikipedia", "https://www.wikipedia.org", "Free online encyclopedia"),
    SpeedDialItem("MIT OpenCourseWare", "https://ocw.mit.edu", "Free lecture notes, exams, and videos from MIT")
)
