package com.heyee.comments.service.captcha;

import cn.hutool.core.lang.UUID;
import com.heyee.comments.dto.ImageCaptchaDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static com.heyee.comments.utils.RedisConstants.LOGIN_IMAGE_CAPTCHA_KEY;
import static com.heyee.comments.utils.RedisConstants.LOGIN_IMAGE_CAPTCHA_TTL;

@Service
public class LoginCaptchaService {

    private static final int WIDTH = 124;
    private static final int HEIGHT = 44;
    private static final String DIGITS = "23456789";

    private final SecureRandom random = new SecureRandom();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public ImageCaptchaDTO create() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForValue().set(
                LOGIN_IMAGE_CAPTCHA_KEY + captchaId, code, LOGIN_IMAGE_CAPTCHA_TTL, TimeUnit.MINUTES);
        return new ImageCaptchaDTO(captchaId, render(code));
    }

    /** A captcha is deleted after every attempt to prevent repeated guessing. */
    public boolean verifyAndConsume(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null || !captchaCode.matches("\\d{4}")) return false;
        String key = LOGIN_IMAGE_CAPTCHA_KEY + captchaId.trim();
        String expected = stringRedisTemplate.opsForValue().getAndDelete(key);
        return expected != null && expected.equals(captchaCode.trim());
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) code.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        return code.toString();
    }

    private String render(String code) {
        BufferedImage source = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 250, 255));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 5; i++) {
                g.setColor(randomSoftColor(80));
                g.drawLine(0, 5 + random.nextInt(HEIGHT - 10), WIDTH, 5 + random.nextInt(HEIGHT - 10));
            }
            for (int i = 0; i < 35; i++) {
                g.setColor(randomSoftColor(120));
                int size = 1 + random.nextInt(2);
                g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), size, size);
            }
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 29);
            for (int i = 0; i < code.length(); i++) {
                AffineTransform original = g.getTransform();
                g.translate(11 + i * 27 + random.nextInt(3), 30 + random.nextInt(5));
                g.rotate((random.nextDouble() - 0.5D) * 0.38D);
                g.shear((random.nextDouble() - 0.5D) * 0.20D, 0D);
                g.setFont(font);
                g.setColor(new Color(25 + random.nextInt(50), 55 + random.nextInt(60), 90 + random.nextInt(70)));
                g.drawString(String.valueOf(code.charAt(i)), 0, 0);
                g.setTransform(original);
            }
        } finally {
            g.dispose();
        }
        float[] blur = {1f / 16f, 2f / 16f, 1f / 16f, 2f / 16f, 4f / 16f, 2f / 16f, 1f / 16f, 2f / 16f, 1f / 16f};
        BufferedImage blurred = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(new Kernel(3, 3, blur), ConvolveOp.EDGE_NO_OP, null).filter(source, blurred);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(blurred, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("图形验证码生成失败", e);
        }
    }

    private Color randomSoftColor(int max) {
        return new Color(
                120 + random.nextInt(Math.min(max, 136)),
                130 + random.nextInt(Math.min(max, 126)),
                155 + random.nextInt(Math.min(max, 90))
        );
    }
}
