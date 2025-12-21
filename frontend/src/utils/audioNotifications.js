/**
 * Audio notification utility for Kitchen Dashboard
 * Uses Web Audio API to generate notification sounds
 */

class AudioNotificationService {
  constructor() {
    this.audioContext = null;
    this.isMuted = false;
    this.isInitialized = false;
  }

  /**
   * Initialize audio context (requires user interaction)
   */
  initialize() {
    if (this.isInitialized) return;

    try {
      // Create audio context
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      this.audioContext = new AudioContext();
      this.isInitialized = true;
      console.log('Audio notification service initialized');
    } catch (error) {
      console.error('Failed to initialize audio context:', error);
    }
  }

  /**
   * Play a beep sound with specified frequency and duration
   */
  playBeep(frequency = 800, duration = 200, volume = 0.3) {
    if (!this.isInitialized || this.isMuted || !this.audioContext) {
      return;
    }

    try {
      const oscillator = this.audioContext.createOscillator();
      const gainNode = this.audioContext.createGain();

      oscillator.connect(gainNode);
      gainNode.connect(this.audioContext.destination);

      oscillator.frequency.value = frequency;
      oscillator.type = 'sine';

      gainNode.gain.setValueAtTime(volume, this.audioContext.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(
        0.01,
        this.audioContext.currentTime + duration / 1000
      );

      oscillator.start(this.audioContext.currentTime);
      oscillator.stop(this.audioContext.currentTime + duration / 1000);
    } catch (error) {
      console.error('Failed to play beep:', error);
    }
  }

  /**
   * Play notification for new order
   * Two ascending beeps
   */
  playNewOrderSound() {
    this.playBeep(600, 150, 0.3);
    setTimeout(() => this.playBeep(800, 150, 0.3), 200);
  }

  /**
   * Play notification for order ready
   * Three quick beeps
   */
  playOrderReadySound() {
    this.playBeep(1000, 100, 0.3);
    setTimeout(() => this.playBeep(1000, 100, 0.3), 150);
    setTimeout(() => this.playBeep(1000, 100, 0.3), 300);
  }

  /**
   * Play notification for urgent priority
   * Rapid alternating beeps (alarm-like)
   */
  playUrgentSound() {
    this.playBeep(900, 150, 0.4);
    setTimeout(() => this.playBeep(700, 150, 0.4), 200);
    setTimeout(() => this.playBeep(900, 150, 0.4), 400);
  }

  /**
   * Play notification for priority change
   * Single higher pitch beep
   */
  playPriorityChangeSound() {
    this.playBeep(1200, 200, 0.3);
  }

  /**
   * Toggle mute state
   */
  toggleMute() {
    this.isMuted = !this.isMuted;
    return this.isMuted;
  }

  /**
   * Set mute state
   */
  setMuted(muted) {
    this.isMuted = muted;
  }

  /**
   * Get mute state
   */
  getMuted() {
    return this.isMuted;
  }

  /**
   * Test all sounds (for user to preview)
   */
  testSounds() {
    console.log('Testing new order sound...');
    this.playNewOrderSound();

    setTimeout(() => {
      console.log('Testing order ready sound...');
      this.playOrderReadySound();
    }, 1000);

    setTimeout(() => {
      console.log('Testing urgent sound...');
      this.playUrgentSound();
    }, 2000);

    setTimeout(() => {
      console.log('Testing priority change sound...');
      this.playPriorityChangeSound();
    }, 3000);
  }
}

// Create singleton instance
const audioNotificationService = new AudioNotificationService();

export default audioNotificationService;
