require("dotenv").config();

const express = require("express");
const cors = require("cors");
const prisma = require("./lib/prisma");

const app = express();

app.use(cors());
app.use(express.json());

// ─── Health check ────────────────────────────────────────
app.get("/", (req, res) => {
  res.json({ ok: true, message: "Backend Hackathon 404 funcionando" });
});

// ─── Crear menor ─────────────────────────────────────────
app.post("/users/minor", async (req, res) => {
  try {
    const { name, email, locationState, locationCity } = req.body;

    const trustCode = Math.random().toString(36).substring(2, 8).toUpperCase();

    const user = await prisma.user.create({
      data: {
        name,
        email,
        role: "MINOR",
        locationState,
        locationCity,
        minorProfile: {
          create: { trustCode }
        }
      },
      include: { minorProfile: true }
    });

    res.status(201).json(user);
  } catch (error) {
    res.status(500).json({ error: "No se pudo crear el menor", details: error.message });
  }
});

// ─── Auto-registro por deviceId (demo) ─────────────────
app.post("/users/minor/auto-register", async (req, res) => {
  try {
    const { deviceId, name } = req.body;

    if (!deviceId) {
      return res.status(400).json({ error: "deviceId es requerido" });
    }

    // Si ya existe un menor con este deviceId, devolver el existente
    const existing = await prisma.minorProfile.findUnique({
      where: { deviceId },
      include: { user: true }
    });

    if (existing) {
      return res.json({ registered: false, user: existing.user, minorProfile: existing });
    }

    // Crear nuevo menor
    const trustCode = Math.random().toString(36).substring(2, 8).toUpperCase();

    const user = await prisma.user.create({
      data: {
        name: name || `Menor-${deviceId.substring(0, 6)}`,
        role: "MINOR",
        minorProfile: {
          create: { trustCode, deviceId }
        }
      },
      include: { minorProfile: true }
    });

    res.status(201).json({ registered: true, user, minorProfile: user.minorProfile });
  } catch (error) {
    res.status(500).json({ error: "Auto-registro fallido", details: error.message });
  }
});

// ─── Crear adulto de confianza ────────────────────────────
app.post("/users/adult", async (req, res) => {
  try {
    const { name, email, phone, relationship } = req.body;

    const user = await prisma.user.create({
      data: {
        name,
        email,
        phone,
        role: "TRUSTED_ADULT",
        adultProfile: {
          create: { relationship }
        }
      },
      include: { adultProfile: true }
    });

    res.status(201).json(user);
  } catch (error) {
    res.status(500).json({ error: "No se pudo crear el adulto", details: error.message });
  }
});

// ─── Vincular adulto con menor via trustCode ──────────────
app.post("/trust-links", async (req, res) => {
  try {
    const { adultUserId, trustCode } = req.body;

    const minorProfile = await prisma.minorProfile.findUnique({
      where: { trustCode }
    });

    if (!minorProfile) {
      return res.status(404).json({ error: "Código de confianza no encontrado" });
    }

    const adultProfile = await prisma.trustedAdultProfile.findUnique({
      where: { userId: adultUserId }
    });

    if (!adultProfile) {
      return res.status(404).json({ error: "Adulto no encontrado" });
    }

    const link = await prisma.trustLink.create({
      data: {
        minorProfileId: minorProfile.id,
        adultProfileId: adultProfile.id
      }
    });

    res.status(201).json(link);
  } catch (error) {
    res.status(500).json({ error: "No se pudo vincular el adulto", details: error.message });
  }
});

// ─── Crear evento de IA ─────────────────────────────
app.post("/ai-events", async (req, res) => {
  try {
    const {
      minorId, source, platform, riskType, riskLevel,
      summary, rawText, emojiTags, evidenceUrl,
      locationState, locationCity, score,
      visionLabel, visionObjects, detectedUser, screenContext
    } = req.body;

    const event = await prisma.aiEvent.create({
      data: {
        minorId, source, platform, riskType, riskLevel,
        summary, rawText,
        emojiTags: emojiTags || [],
        evidenceUrl, locationState, locationCity, score,
        visionLabel,
        visionObjects: visionObjects || [],
        detectedUser,
        screenContext
      }
    });

    // Si riskLevel es HIGH o CRITICAL, crear alertas para adultos vinculados
    if (riskLevel === "HIGH" || riskLevel === "CRITICAL") {
      const minorProfile = await prisma.minorProfile.findFirst({
        where: { userId: minorId },
        include: { links: { include: { adultProfile: true } } }
      });

      if (minorProfile) {
        const alertPromises = minorProfile.links.map(link =>
          prisma.alert.create({
            data: {
              aiEventId: event.id,
              adultId: link.adultProfile.userId,
              title: `Alerta ${riskLevel}: ${riskType}`,
              message: summary,
              status: "PENDING"
            }
          })
        );
        await Promise.all(alertPromises);
      }
    }

    res.status(201).json(event);
  } catch (error) {
    res.status(500).json({ error: "No se pudo crear el evento de IA", details: error.message });
  }
});

// ─── Listar eventos de IA ───────────────────────────
app.get("/ai-events", async (req, res) => {
  try {
    const events = await prisma.aiEvent.findMany({
      orderBy: { createdAt: "desc" },
      include: { minor: true, alerts: true, reports: true }
    });
    res.json(events);
  } catch (error) {
    res.status(500).json({ error: "No se pudieron obtener los eventos", details: error.message });
  }
});

// ─── Listar eventos por menor ───────────────────────
app.get("/ai-events/minor/:minorId", async (req, res) => {
  try {
    const events = await prisma.aiEvent.findMany({
      where: { minorId: req.params.minorId },
      orderBy: { createdAt: "desc" },
      include: { alerts: true }
    });
    res.json(events);
  } catch (error) {
    res.status(500).json({ error: "No se pudieron obtener los eventos", details: error.message });
  }
});

// ─── Listar todos los menores ─────────────────────────────
app.get("/users/minors", async (req, res) => {
  try {
    const minors = await prisma.user.findMany({
      where: { role: "MINOR" },
      include: { minorProfile: true }
    });
    res.json(minors);
  } catch (error) {
    res.status(500).json({ error: "No se pudieron obtener los menores", details: error.message });
  }
});

// ─── Obtener un menor por ID ──────────────────────────────
app.get("/users/minors/:id", async (req, res) => {
  try {
    const minor = await prisma.user.findFirst({
      where: { id: req.params.id, role: "MINOR" },
      include: {
        minorProfile: {
          include: { links: true }
        },
        aiEvents: true
      }
    });

    if (!minor) {
      return res.status(404).json({ error: "Menor no encontrado" });
    }

    res.json(minor);
  } catch (error) {
    res.status(500).json({ error: "Error al obtener el menor", details: error.message });
  }
});


// ─── Listar todos los adultos ─────────────────────────────
app.get("/users/adults", async (req, res) => {
  try {
    const adults = await prisma.user.findMany({
      where: { role: "TRUSTED_ADULT" },
      include: { adultProfile: true }
    });
    res.json(adults);
  } catch (error) {
    res.status(500).json({ error: "No se pudieron obtener los adultos", details: error.message });
  }
});

// ─── Obtener un adulto por ID ─────────────────────────────
app.get("/users/adults/:id", async (req, res) => {
  try {
    const adult = await prisma.user.findFirst({
      where: { id: req.params.id, role: "TRUSTED_ADULT" },
      include: {
        adultProfile: {
          include: { links: true }
        },
        alertsReceived: true,
        reportsCreated: true
      }
    });

    if (!adult) {
      return res.status(404).json({ error: "Adulto no encontrado" });
    }

    res.json(adult);
  } catch (error) {
    res.status(500).json({ error: "Error al obtener el adulto", details: error.message });
  }
});

// ─── Crear reporte de seguridad ───────────────────────────
app.post("/reports", async (req, res) => {
  try {
    const { aiEventId, createdById, authorityArea, description } = req.body;

    const report = await prisma.safetyReport.create({
      data: { aiEventId, createdById, authorityArea, description }
    });

    res.status(201).json(report);
  } catch (error) {
    res.status(500).json({ error: "No se pudo crear el reporte", details: error.message });
  }
});

// ─── Listar reportes (panel autoridad) ───────────────────
app.get("/reports", async (req, res) => {
  try {
    const reports = await prisma.safetyReport.findMany({
      orderBy: { createdAt: "desc" },
      include: { aiEvent: true, createdBy: true }
    });
    res.json(reports);
  } catch (error) {
    res.status(500).json({ error: "No se pudieron obtener los reportes", details: error.message });
  }
});

// ─── Start ────────────────────────────────────────────────
const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log(`Servidor corriendo en http://localhost:${port}`);
});